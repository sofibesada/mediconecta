package ar.com.mediconecta.integracion;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Reprueba de la pasarela de pagos (MED-90): los cobros con resultado incierto, contra el
 * sistema levantado con Docker Compose. Lo que se mide es la PASARELA: cuántos cobros
 * aprobados (no devueltos) quedan para cada turno. Tiene que ser exactamente uno.
 *
 * Los casos que no se pueden provocar desde la pantalla (un pedido que la pasarela recibe
 * tarde, un intento de una versión anterior sin clave) se arman directamente: el intento
 * PENDIENTE se inserta en la base y el pedido "que llega tarde" se le manda a la pasarela.
 * La caída de la pasarela se simula con la system property pagos.mock.fallar (jboss-cli).
 *
 * Corre con -Dapi.base=... y necesita el comando docker (como en el CI).
 */
@EnabledIfSystemProperty(named = "api.base", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class RepruebaPagosApiTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final AtomicInteger HORA = new AtomicInteger();
    private static final String VISA = "4111111111111111";
    /** Las fechas se guardan en hora de la clínica (la base corre en UTC). */
    private static final java.time.ZoneId HORA_CLINICA = java.time.ZoneId.of("America/Argentina/Buenos_Aires");

    private String tokenPaciente;
    private String tokenProfesional;
    private long pacienteId;
    private String urlPasarela;
    private String apiKeyPasarela;

    @BeforeAll
    void preparar() throws Exception {
        Assumptions.assumeTrue(docker("version").ok(), "Hace falta el comando docker para simular las fallas");
        RestAssured.baseURI = System.getProperty("api.base");
        urlPasarela = System.getProperty("api.base").replace("/mediconecta/api", "/pagos-mock/api/v1");
        String clave = docker("compose", "exec", "-T", "app", "printenv", "PAGOS_API_KEY").salida().trim();
        apiKeyPasarela = clave.isEmpty() ? "pagos-demo" : clave;

        String sufijo = String.valueOf(System.currentTimeMillis());
        String tokenAdmin = login(requerida("admin.email"), requerida("admin.password"));
        String emailProf = "rp_prof_" + sufijo + "@test.local";
        con(tokenAdmin).body(Map.of("nombre", "Profesional Reprueba", "email", emailProf, "passwordTemporal", "Temp1234"))
                .post("/admin/profesionales").then().statusCode(200);
        tokenProfesional = con(login(emailProf, "Temp1234"))
                .body(Map.of("passwordActual", "Temp1234", "passwordNueva", "Prueba1234"))
                .post("/usuarios/cambiar-password").then().statusCode(200).extract().path("token");
        tokenPaciente = given().contentType(ContentType.JSON)
                .body(Map.of("nombre", "Paciente Reprueba", "email", "rp_pac_" + sufijo + "@test.local", "password", "Prueba1234"))
                .post("/usuarios").then().statusCode(200).extract().path("token");
        pacienteId = ((Number) con(tokenPaciente).get("/perfil").path("id")).longValue();
    }

    // ---------------------------------------------------------------- pasarela caída

    @Test
    void rp01_conLaPasarelaCaidaNoSeCobraYDespuesSePagaUnaSolaVez() throws Exception {
        long turno = turnoConfirmado();
        propiedad("pagos.mock.fallar", "true");
        try {
            Response r = pagar(turno);
            assertEquals(503, r.statusCode(), r.asString());
            assertTrue(r.contentType().startsWith("application/problem+json"));
        } finally {
            sinPropiedad("pagos.mock.fallar");
        }
        assertEquals(0, aprobadosEnLaPasarela(turno), "con la pasarela caída no tiene que haber cobros");

        assertEquals(201, pagar(turno).statusCode());
        assertEquals(1, aprobadosEnLaPasarela(turno));
        assertEquals(List.of("APROBADO"), estadosEnMediConecta(turno));
    }

    // ---------------------------------------------------------------- intentos con resultado incierto

    @Test
    void rp02_unIntentoQueLaPasarelaAproboSinQueMediConectaSeEnterareSeConciliaSinCobrarDeNuevo() throws Exception {
        long turno = turnoConfirmado();
        String clave = "rp02-" + UUID.randomUUID();
        insertarIntentoPendiente(turno, clave, 10);
        assertEquals("APROBADO", cobroDirecto(turno, clave, VISA), "el pedido viejo llegó a la pasarela");

        Response r = pagar(turno);
        assertEquals(201, r.statusCode(), r.asString());
        assertEquals(1, aprobadosEnLaPasarela(turno), "no se cobra por segunda vez");
        assertEquals(List.of("APROBADO"), estadosEnMediConecta(turno));
    }

    @Test
    void rp03_unIntentoQueNuncaLlegoSeAnulaYSiLlegaTardeNoCobra() throws Exception {
        long turno = turnoConfirmado();
        String clave = "rp03-" + UUID.randomUUID();
        insertarIntentoPendiente(turno, clave, 10);

        assertEquals(201, pagar(turno).statusCode(), "pasada la ventana, el intento se anula y se puede pagar");
        assertEquals("ANULADO", cobroDirecto(turno, clave, VISA), "el pedido viejo que llega tarde no cobra");
        assertEquals(1, aprobadosEnLaPasarela(turno));
        List<String> estados = estadosEnMediConecta(turno);
        assertTrue(estados.contains("APROBADO") && estados.contains("FALLIDO"), "estados: " + estados);
    }

    @Test
    void rp04_unIntentoRecienteSinResultadoBloqueaOtroCobro() throws Exception {
        long turno = turnoConfirmado();
        insertarIntentoPendiente(turno, "rp04-" + UUID.randomUUID(), 0);

        Response r = pagar(turno);
        assertEquals(409, r.statusCode(), r.asString());
        assertTrue(r.asString().contains("todavía se está procesando"), r.asString());
        assertEquals(0, aprobadosEnLaPasarela(turno));
    }

    // ---------------------------------------------------------------- intentos de la versión anterior (D-11)

    @Test
    void rp05_unIntentoViejoSinClaveQueYaSeCobroNoSeCobraDeNuevo() throws Exception {
        long turno = turnoConfirmado();
        insertarIntentoPendiente(turno, null, 10);
        assertEquals("APROBADO", cobroDirecto(turno, "version-anterior-" + UUID.randomUUID(), VISA));

        assertEquals(201, pagar(turno).statusCode());
        assertEquals(1, aprobadosEnLaPasarela(turno));
    }

    @Test
    void rp06_siElCobroDeUnIntentoViejoSinClaveLlegaTardeSeDevuelveSolo() throws Exception {
        long turno = turnoConfirmado();
        insertarIntentoPendiente(turno, null, 10);

        assertEquals(201, pagar(turno).statusCode(), "sin rastro en la pasarela, el intento viejo se da por fallido");
        // El pedido de la versión anterior llega ahora: no se puede anular (no hay clave guardada)
        // y la pasarela lo cobra. Queda un segundo cobro del mismo turno.
        assertEquals("APROBADO", cobroDirecto(turno, "version-anterior-" + UUID.randomUUID(), VISA));

        // La conciliación (cada minuto) tiene que detectarlo y devolverlo.
        long limite = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(90);
        while (aprobadosEnLaPasarela(turno) > 1 && System.currentTimeMillis() < limite) {
            Thread.sleep(5_000);
        }
        assertEquals(1, aprobadosEnLaPasarela(turno), "el cobro duplicado tiene que devolverse");
        List<String> estados = estadosEnMediConecta(turno);
        assertTrue(estados.contains("REEMBOLSADO") && estados.contains("APROBADO"), "estados: " + estados);
    }

    // ---------------------------------------------------------------- ayudas

    private Response pagar(long turno) {
        return con(tokenPaciente).header("Idempotency-Key", "rp-" + UUID.randomUUID())
                .body(Map.of("turnoId", turno, "numero", VISA, "vencimiento", "12/30", "cvv", "123", "titular", "PACIENTE REPRUEBA"))
                .post("/pagos");
    }

    /** Cobros aprobados y no devueltos que la pasarela tiene para el turno. */
    private long aprobadosEnLaPasarela(long turno) {
        List<String> estados = pasarela().queryParam("referencia", "turno-" + turno).get("/pagos")
                .then().statusCode(200).extract().jsonPath().getList("estado");
        return estados.stream().filter("APROBADO"::equals).count();
    }

    /** Pedido de cobro que llega directo a la pasarela (como uno viejo que llegó tarde). */
    private String cobroDirecto(long turno, String clave, String numero) {
        Response r = pasarela().header("Idempotency-Key", clave)
                .body(Map.of("monto", importe(turno), "moneda", "ARS", "referencia", "turno-" + turno,
                        "tarjeta", Map.of("numero", numero, "vencimiento", "12/30", "cvv", "123", "titular", "PACIENTE REPRUEBA")))
                .post("/pagos");
        assertTrue(r.statusCode() == 200 || r.statusCode() == 201, r.asString());
        return r.path("estado");
    }

    private List<String> estadosEnMediConecta(long turno) {
        return con(tokenPaciente).get("/pagos/turno/" + turno).then().statusCode(200).extract().jsonPath().getList("estado");
    }

    private Object importe(long turno) {
        return con(tokenPaciente).get("/turnos/" + turno).path("importeCopago");
    }

    /** Un intento PENDIENTE como el que deja un cobro sin respuesta (clave null = versión anterior). */
    private void insertarIntentoPendiente(long turno, String clave, int minutosAtras) throws Exception {
        String sql = "INSERT INTO pago (turno_id, paciente_id, importe, estado, marca, ultimos4, creado_en, clave_externa) "
                + "VALUES (" + turno + ", " + pacienteId + ", " + importe(turno) + ", 'PENDIENTE', 'Visa', '1111', "
                + "'" + LocalDateTime.now(HORA_CLINICA).minusMinutes(minutosAtras).format(ISO) + "', " + (clave == null ? "NULL" : "'" + clave + "'") + ")";
        String usuario = docker("compose", "exec", "-T", "db", "printenv", "POSTGRES_USER").salida().trim();
        // El SQL va por la entrada estándar: así no depende de cómo cada sistema pasa las comillas.
        Resultado r = dockerConEntrada(sql + ";\n", "compose", "exec", "-T", "db", "psql", "-U", usuario, "-d", "mediconecta", "-v", "ON_ERROR_STOP=1");
        assertTrue(r.ok(), "no se pudo insertar el intento: " + r.salida());
    }

    private void propiedad(String nombre, String valor) throws Exception {
        sinPropiedad(nombre);
        jbossCli("/system-property=" + nombre + ":add(value=" + valor + ")");
    }

    private void sinPropiedad(String nombre) throws Exception {
        jbossCli("/system-property=" + nombre + ":remove");
    }

    private void jbossCli(String comando) throws Exception {
        docker("compose", "exec", "-T", "app", "/opt/jboss/wildfly/bin/jboss-cli.sh", "--connect", "--command=" + comando);
    }

    record Resultado(int codigo, String salida) {
        boolean ok() {
            return codigo == 0;
        }
    }

    private static Resultado docker(String... args) throws IOException, InterruptedException {
        return dockerConEntrada(null, args);
    }

    private static Resultado dockerConEntrada(String entrada, String... args) throws IOException, InterruptedException {
        String[] comando = new String[args.length + 1];
        comando[0] = "docker";
        System.arraycopy(args, 0, comando, 1, args.length);
        Process p;
        try {
            p = new ProcessBuilder(comando).redirectErrorStream(true).start();
        } catch (IOException e) {
            return new Resultado(-1, e.getMessage());
        }
        try (var in = p.getOutputStream()) {
            if (entrada != null) {
                in.write(entrada.getBytes(StandardCharsets.UTF_8));
            }
        }
        String salida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return new Resultado(-1, "docker no respondió");
        }
        return new Resultado(p.exitValue(), salida);
    }

    private RequestSpecification pasarela() {
        return given().baseUri(urlPasarela).contentType(ContentType.JSON).header("Authorization", "Bearer " + apiKeyPasarela);
    }

    private long publicar() {
        return ((Number) con(tokenProfesional).body(Map.of("fechaHora", fecha(30)))
                .post("/turnos").then().statusCode(200).extract().path("id")).longValue();
    }

    private long turnoConfirmado() {
        long turno = publicar();
        con(tokenPaciente).post("/turnos/" + turno + "/reservar").then().statusCode(200);
        con(tokenPaciente).post("/turnos/" + turno + "/confirmar?particular=true").then().statusCode(200);
        return turno;
    }

    /** Fecha a "dias" dias, con una hora distinta en cada llamada para que no choquen. */
    private static String fecha(int dias) {
        int n = HORA.incrementAndGet();
        return LocalDateTime.now().plusDays(dias + 40 + n / 10).withHour(8 + n % 10).withMinute(30).withSecond(0).withNano(0).format(ISO);
    }

    private RequestSpecification con(String token) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + token);
    }

    private String login(String email, String password) {
        return given().contentType(ContentType.JSON).body(Map.of("email", email, "password", password))
                .post("/usuarios/login").then().statusCode(200).extract().path("token");
    }

    private static String requerida(String propiedad) {
        String v = System.getProperty(propiedad);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Falta -D" + propiedad);
        }
        return v;
    }
}
