package ar.com.mediconecta.seguridad;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Pruebas de seguridad de la Clase 11 contra la API desplegada en el WildFly local.
 *
 * Solo corren si se pasa -Dapi.base=...; en un build normal se saltean. Cada caso deja
 * una linea en target/seguridad/evidencia.md con request, esperado y obtenido, sin
 * tokens ni contrasenas. Los datos son ficticios y se crean en cada corrida.
 *
 * Como ejecutarlas: ver src/test/README-seguridad.md.
 */
@EnabledIfSystemProperty(named = "api.base", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class SeguridadApiTest {

    private static final Path EVIDENCIA = Path.of("target", "seguridad", "evidencia.md");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final long ID_INEXISTENTE = 999_999_999L;

    private String tokenAdmin;
    private Usuario pacienteA;
    private Usuario pacienteB;
    private Usuario pacienteDescartable;
    private Usuario profesional;
    private long turnoDeA;

    private record Usuario(long id, String token) {}

    // ------------------------------------------------------------------ preparacion

    @BeforeAll
    void preparar() throws IOException {
        RestAssured.baseURI = System.getProperty("api.base");
        Files.createDirectories(EVIDENCIA.getParent());
        Files.writeString(EVIDENCIA, "| ID | Caso | Request | Esperado | Obtenido |\n|---|---|---|---|---|\n", StandardCharsets.UTF_8);

        String sufijo = String.valueOf(System.currentTimeMillis());
        tokenAdmin = login(requerida("admin.email"), requerida("admin.password")).path("token");
        assertNotNull(tokenAdmin, "No se pudo iniciar sesion como admin");

        pacienteA = registrar("Paciente A Prueba", "sec_a_" + sufijo + "@test.local");
        pacienteB = registrar("Paciente B Prueba", "sec_b_" + sufijo + "@test.local");
        pacienteDescartable = registrar("Paciente Descartable", "sec_d_" + sufijo + "@test.local");

        String emailProf = "sec_prof_" + sufijo + "@test.local";
        long idProf = ((Number) con(tokenAdmin).body(json(Map.of("nombre", "Profesional Prueba", "email", emailProf, "passwordTemporal", "Temp1234")))
                .post("/admin/profesionales").then().statusCode(200).extract().path("id")).longValue();
        String tokenTemporal = login(emailProf, "Temp1234").path("token");
        String tokenProf = con(tokenTemporal).body(json(Map.of("passwordActual", "Temp1234", "passwordNueva", "Prueba1234")))
                .post("/usuarios/cambiar-password").then().statusCode(200).extract().path("token");
        profesional = new Usuario(idProf, tokenProf);

        String fecha = LocalDateTime.now().plusDays(40).withHour(10).withMinute(0).withSecond(0).withNano(0).format(ISO);
        turnoDeA = ((Number) con(profesional.token()).body(json(Map.of("fechaHora", fecha)))
                .post("/turnos").then().statusCode(200).extract().path("id")).longValue();
        con(pacienteA.token()).body(json(Map.of("turnoId", turnoDeA))).post("/turnos/agendar").then().statusCode(200);
        con(pacienteA.token()).post("/turnos/" + turnoDeA + "/confirmar").then().statusCode(200);
    }

    // ------------------------------------------------------------------ SEC-01

    @Test
    void sec01a_sinToken_operacionProtegida_401_sinDatos() {
        Response r = given().get("/notificaciones");
        evidencia("SEC-01", "Sin token, lectura protegida", "GET /notificaciones (sin Authorization)", "401 y solo {error}", r);
        assertEquals(401, r.statusCode());
        assertEquals(List.of("error"), List.copyOf(r.jsonPath().getMap("$").keySet()));
    }

    @Test
    void sec01b_sinToken_noEjecutaOperacion() {
        int antes = turnosDelProfesional();
        Response r = given().contentType(ContentType.JSON)
                .body(json(Map.of("fechaHora", LocalDateTime.now().plusDays(50).withNano(0).format(ISO)))).post("/turnos");
        int despues = turnosDelProfesional();
        evidencia("SEC-01", "Sin token, escritura protegida", "POST /turnos (sin Authorization)",
                "401 y la agenda no cambia", r, "turnos antes=" + antes + ", despues=" + despues);
        assertEquals(401, r.statusCode());
        assertEquals(antes, despues);
    }

    // ------------------------------------------------------------------ SEC-02

    @Test
    void sec02a_duracionDelToken_maximo15Minutos() {
        Map<String, Object> claims = payload(pacienteA.token());
        long duracion = ((Number) claims.get("exp")).longValue() - ((Number) claims.get("iat")).longValue();
        evidencia("SEC-02", "Duracion del token emitido", "POST /usuarios (registro) -> exp - iat",
                "<= 900 s", null, "exp - iat = " + duracion + " s");
        assertTrue(duracion <= 900, "El token dura " + duracion + " s; el criterio pide como maximo 900 s");
    }

    @Test
    void sec02b_tokenVencido_401() {
        String secreto = System.getenv("MEDICONECTA_JWT_SECRET");
        Assumptions.assumeTrue(secreto != null && secreto.length() >= 32,
                "Falta MEDICONECTA_JWT_SECRET (la misma clave configurada en WildFly): no se puede firmar un token vencido valido");
        long ahora = Instant.now().getEpochSecond();
        String vencido = firmar(secreto, Map.of("sub", String.valueOf(pacienteA.id()), "iat", ahora - 1000, "exp", ahora - 60));
        Response r = con(vencido).get("/notificaciones");
        evidencia("SEC-02", "Token vencido con firma valida", "GET /notificaciones (exp en el pasado)", "401 sin datos", r);
        assertEquals(401, r.statusCode());
        assertSinDatos(r);
    }

    @Test
    void sec02c_firmaAlterada_payloadModificado_401() {
        String[] p = pacienteA.token().split("\\.");
        Map<String, Object> claims = new java.util.LinkedHashMap<>(payload(pacienteA.token()));
        claims.put("sub", String.valueOf(pacienteB.id()));
        String alterado = p[0] + "." + b64(json(claims)) + "." + p[2];
        Response r = con(alterado).get("/notificaciones");
        evidencia("SEC-02", "Payload cambiado (otro usuario), firma original", "GET /notificaciones", "401 sin datos", r);
        assertEquals(401, r.statusCode());
        assertSinDatos(r);
    }

    @Test
    void sec02d_firmaAlterada_firmaModificada_401() {
        String token = pacienteA.token();
        int medio = token.lastIndexOf('.') + 10;
        char c = token.charAt(medio);
        String alterado = token.substring(0, medio) + (c == 'A' ? 'B' : 'A') + token.substring(medio + 1);
        Response r = con(alterado).get("/notificaciones");
        evidencia("SEC-02", "Firma modificada", "GET /notificaciones", "401 sin datos", r);
        assertEquals(401, r.statusCode());
        assertSinDatos(r);
    }

    @Test
    void sec02e_algoritmoNone_401() {
        long ahora = Instant.now().getEpochSecond();
        String header = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String sinFirma = header + "." + b64(json(Map.of("sub", String.valueOf(pacienteA.id()), "iat", ahora, "exp", ahora + 600))) + ".";
        Response r = con(sinFirma).get("/notificaciones");
        evidencia("SEC-02", "alg none sin firma", "GET /notificaciones", "401 sin datos", r);
        assertEquals(401, r.statusCode());
        assertSinDatos(r);
    }

    @Test
    void sec02f_controlPositivo_tokenValido_200() {
        Response r = con(pacienteA.token()).get("/notificaciones");
        evidencia("SEC-02", "Control positivo: token valido", "GET /notificaciones", "200", r);
        assertEquals(200, r.statusCode());
    }

    // ------------------------------------------------------------------ SEC-03

    @Test
    void sec03a_paciente_desactivarUsuario_403_sinCambios() {
        boolean antes = activo(pacienteDescartable.id());
        Response r = con(pacienteA.token()).body(json(Map.of("activo", false)))
                .post("/admin/usuarios/" + pacienteDescartable.id() + "/activo");
        boolean despues = activo(pacienteDescartable.id());
        evidencia("SEC-03", "PACIENTE llama operacion exclusiva ADMIN (mutacion)",
                "POST /admin/usuarios/{id}/activo {activo:false}", "403 y el usuario sigue activo", r,
                "activo antes=" + antes + ", despues=" + despues);
        assertEquals(403, r.statusCode());
        assertTrue(antes);
        assertEquals(antes, despues);
    }

    @Test
    void sec03b_paciente_listarUsuarios_403() {
        Response r = con(pacienteA.token()).get("/admin/usuarios");
        evidencia("SEC-03", "PACIENTE llama operacion exclusiva ADMIN (lectura)", "GET /admin/usuarios", "403 sin datos", r);
        assertEquals(403, r.statusCode());
        assertFalse(r.asString().contains("@test.local"), "La respuesta expone datos de usuarios");
    }

    @Test
    void sec03c_controlPositivo_admin_200() {
        Response r = con(tokenAdmin).body(json(Map.of("activo", false))).post("/admin/usuarios/" + pacienteDescartable.id() + "/activo");
        boolean despues = activo(pacienteDescartable.id());
        evidencia("SEC-03", "Control positivo: ADMIN desactiva al usuario descartable",
                "POST /admin/usuarios/{id}/activo {activo:false}", "200 y el usuario queda inactivo", r, "activo despues=" + despues);
        con(tokenAdmin).body(json(Map.of("activo", true))).post("/admin/usuarios/" + pacienteDescartable.id() + "/activo");
        assertEquals(200, r.statusCode());
        assertFalse(despues);
    }

    // ------------------------------------------------------------------ SEC-04

    @Test
    void sec04a_controlPositivo_propietario_200() {
        Response r = con(pacienteA.token()).get("/turnos/paciente/" + pacienteA.id());
        evidencia("SEC-04", "Control positivo: A consulta sus turnos", "GET /turnos/paciente/{A}", "200 con el turno de A", r);
        assertEquals(200, r.statusCode());
        assertTrue(r.jsonPath().getList("id", Number.class).stream().anyMatch(n -> n.longValue() == turnoDeA));
    }

    @Test
    void sec04b_ajeno_noDevuelveDatos() {
        Response r = con(pacienteB.token()).get("/turnos/paciente/" + pacienteA.id());
        evidencia("SEC-04", "B consulta los turnos de A", "GET /turnos/paciente/{A} con token de B",
                "rechazo sin datos del turno", r);
        assertTrue(r.statusCode() >= 400, "B obtuvo datos de A");
        assertFalse(r.asString().contains("\"id\":" + turnoDeA), "La respuesta incluye el turno de A");
    }

    @Test
    void sec04c_ajeno_responde404() {
        Response r = con(pacienteB.token()).get("/turnos/paciente/" + pacienteA.id());
        evidencia("SEC-04", "B consulta los turnos de A: codigo", "GET /turnos/paciente/{A} con token de B", "404", r);
        assertEquals(404, r.statusCode(), "El criterio pide 404 para no confirmar la existencia del recurso");
    }

    @Test
    void sec04d_porIdDeTurno_noConfirmaExistencia() {
        Response ajeno = con(pacienteB.token()).get("/turnos/" + turnoDeA + "/sala");
        Response inexistente = con(pacienteB.token()).get("/turnos/" + ID_INEXISTENTE + "/sala");
        evidencia("SEC-04", "B consulta por id un turno de A vs un id inexistente", "GET /turnos/{id}/sala",
                "misma respuesta en los dos casos", ajeno, "inexistente: " + inexistente.statusCode() + " " + inexistente.asString());
        assertEquals(inexistente.statusCode(), ajeno.statusCode());
        assertEquals(inexistente.asString(), ajeno.asString());
    }

    @Test
    void sec04e_porIdDeTurno_responde404() {
        Response r = con(pacienteB.token()).get("/turnos/" + turnoDeA + "/sala");
        evidencia("SEC-04", "B consulta por id un turno de A: codigo", "GET /turnos/{id}/sala con token de B", "404", r);
        assertEquals(404, r.statusCode());
    }

    @Test
    void sec04f_registraElRechazo() throws IOException, InterruptedException {
        String log = System.getProperty("wildfly.log");
        Assumptions.assumeTrue(log != null && Files.exists(Path.of(log)), "Falta -Dwildfly.log=<ruta a server.log>");
        long desde = Files.size(Path.of(log));
        con(pacienteB.token()).get("/turnos/paciente/" + pacienteA.id());
        Thread.sleep(1000);
        String nuevo = leerDesde(Path.of(log), desde);
        boolean registrado = nuevo.lines().anyMatch(l -> l.matches("(?i).*(rechaz|deneg|prohib|forbidden|acceso).*")
                && (l.contains(String.valueOf(pacienteB.id())) || l.contains("/turnos/paciente/" + pacienteA.id())));
        evidencia("SEC-04", "El rechazo queda registrado en el log", "server.log tras GET /turnos/paciente/{A} con token de B",
                "una linea que registre el rechazo, sin datos sensibles", null,
                registrado ? "hay registro" : "sin registro del rechazo (" + nuevo.lines().count() + " lineas nuevas)");
        assertTrue(registrado, "No se encontro un registro del acceso rechazado");
    }

    // ------------------------------------------------------------------ helpers

    private RequestSpecification con(String token) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + token);
    }

    private Response login(String email, String password) {
        return given().contentType(ContentType.JSON).body(json(Map.of("email", email, "password", password)))
                .post("/usuarios/login").then().statusCode(200).extract().response();
    }

    private Usuario registrar(String nombre, String email) {
        Response r = given().contentType(ContentType.JSON).body(json(Map.of("nombre", nombre, "email", email, "password", "Prueba1234")))
                .post("/usuarios").then().statusCode(200).extract().response();
        return new Usuario(((Number) r.path("id")).longValue(), r.path("token"));
    }

    private int turnosDelProfesional() {
        return con(profesional.token()).get("/turnos/profesional/" + profesional.id()).then().statusCode(200)
                .extract().jsonPath().getList("$").size();
    }

    private boolean activo(long usuarioId) {
        List<Map<String, Object>> usuarios = con(tokenAdmin).get("/admin/usuarios").then().statusCode(200).extract().jsonPath().getList("$");
        return usuarios.stream().filter(u -> ((Number) u.get("id")).longValue() == usuarioId)
                .map(u -> (Boolean) u.get("activo")).findFirst().orElseThrow();
    }

    private void assertSinDatos(Response r) {
        Map<String, Object> cuerpo = r.jsonPath().getMap("$");
        assertEquals(List.of("error"), List.copyOf(cuerpo.keySet()), "La respuesta trae algo mas que el mensaje de error");
    }

    private static Map<String, Object> payload(String token) {
        String json = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        return new io.restassured.path.json.JsonPath(json).getMap("$");
    }

    private static String firmar(String secreto, Map<String, Object> claims) {
        String contenido = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}") + "." + b64(json(claims));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return contenido + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String json(Map<String, ?> m) {
        StringBuilder sb = new StringBuilder("{");
        m.forEach((k, v) -> sb.append(sb.length() > 1 ? "," : "").append('"').append(k).append("\":")
                .append(v instanceof String ? "\"" + v + "\"" : String.valueOf(v)));
        return sb.append('}').toString();
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String requerida(String propiedad) {
        String v = System.getProperty(propiedad);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Falta -D" + propiedad);
        }
        return v;
    }

    private static String leerDesde(Path archivo, long desde) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(archivo.toFile(), "r")) {
            f.seek(desde);
            byte[] b = new byte[(int) (f.length() - desde)];
            f.readFully(b);
            return new String(b, StandardCharsets.UTF_8);
        }
    }

    /** Una fila de evidencia. Nunca escribe tokens: solo codigo HTTP y cuerpo (que no debe traer datos). */
    private static void evidencia(String id, String caso, String request, String esperado, Response r, String... extra) {
        String obtenido = r == null ? "" : r.statusCode() + " " + r.asString().replace("|", "/").replace("\n", " ");
        if (extra.length > 0) {
            obtenido = (obtenido.isEmpty() ? "" : obtenido + "; ") + String.join("; ", extra);
        }
        String fila = "| " + id + " | " + caso + " | `" + request + "` | " + esperado + " | " + obtenido + " |\n";
        try {
            Files.writeString(EVIDENCIA, fila, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
