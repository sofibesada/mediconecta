package ar.com.mediconecta.integracion;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Contrato de la API para la entrega del 30/11, contra el sistema levantado:
 * Problem Details, ETag / If-Match, Idempotency-Key y proteccion XXE del servicio SOAP.
 *
 * Igual que las pruebas de seguridad, solo corren con -Dapi.base=... (en el CI, contra
 * Docker Compose). Cada prueba crea sus propios datos.
 */
@EnabledIfSystemProperty(named = "api.base", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegracionApiTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final AtomicInteger HORA = new AtomicInteger();

    private String tokenPaciente;
    private String tokenProfesional;

    @BeforeAll
    void preparar() {
        RestAssured.baseURI = System.getProperty("api.base");
        String sufijo = String.valueOf(System.currentTimeMillis());
        String tokenAdmin = login(requerida("admin.email"), requerida("admin.password"));

        String emailProf = "int_prof_" + sufijo + "@test.local";
        con(tokenAdmin).body(json(Map.of("nombre", "Profesional Integracion", "email", emailProf, "passwordTemporal", "Temp1234")))
                .post("/admin/profesionales").then().statusCode(200);
        tokenProfesional = con(login(emailProf, "Temp1234"))
                .body(json(Map.of("passwordActual", "Temp1234", "passwordNueva", "Prueba1234")))
                .post("/usuarios/cambiar-password").then().statusCode(200).extract().path("token");

        tokenPaciente = given().contentType(ContentType.JSON)
                .body(json(Map.of("nombre", "Paciente Integracion", "email", "int_pac_" + sufijo + "@test.local", "password", "Prueba1234")))
                .post("/usuarios").then().statusCode(200).extract().path("token");
    }

    // ------------------------------------------------------------------ Problem Details

    @Test
    void int01_losErroresVienenEnFormatoProblemDetails() {
        long turno = turnoConfirmado();
        // Confirmar de nuevo un turno confirmado es una transicion invalida: 409.
        Response r = con(tokenPaciente).post("/turnos/" + turno + "/confirmar?particular=true");

        assertEquals(409, r.statusCode());
        assertTrue(r.contentType().startsWith("application/problem+json"), "Content-Type: " + r.contentType());
        assertEquals("about:blank", r.path("type"));
        assertEquals("Conflict", r.path("title"));
        assertEquals(409, (int) r.path("status"));
        String detalle = r.path("detail");
        String error = r.path("error");
        assertNotNull(detalle);
        assertEquals(detalle, error, "\"error\" se mantiene igual a \"detail\" por compatibilidad");
    }

    // ------------------------------------------------------------------ ETag

    @Test
    void int02_unTurnoTraeEtagYSinCambiosResponde304() {
        long turno = publicar();
        Response r = con(tokenProfesional).get("/turnos/" + turno);
        assertEquals(200, r.statusCode());
        String etag = r.header("ETag");
        assertNotNull(etag, "Falta el encabezado ETag");

        Response igual = con(tokenProfesional).header("If-None-Match", etag).get("/turnos/" + turno);
        assertEquals(304, igual.statusCode());
        assertEquals("", igual.asString());
    }

    @Test
    void int03_conUnaVersionViejaNoSePisaElCambioDeOtro() {
        long turno = publicar();
        String etagViejo = con(tokenProfesional).get("/turnos/" + turno).header("ETag");

        // Otro cambio (por ejemplo, desde otra pestaña) mueve el turno: la version avanza.
        Response cambio = con(tokenProfesional).header("If-Match", etagViejo)
                .body(json(Map.of("fechaHora", fecha(50)))).put("/turnos/" + turno + "/reprogramar");
        assertEquals(200, cambio.statusCode());
        assertNotEquals(etagViejo, cambio.header("ETag"), "La version tiene que cambiar");

        // Con la version vieja: 412 y el turno no cambia.
        String fechaAntes = con(tokenProfesional).get("/turnos/" + turno).path("fechaHora");
        Response viejo = con(tokenProfesional).header("If-Match", etagViejo)
                .body(json(Map.of("fechaHora", fecha(60)))).put("/turnos/" + turno + "/reprogramar");
        assertEquals(412, viejo.statusCode());
        assertTrue(viejo.contentType().startsWith("application/problem+json"));
        assertEquals(fechaAntes, con(tokenProfesional).get("/turnos/" + turno).path("fechaHora"));
    }

    // ------------------------------------------------------------------ Idempotency-Key

    @Test
    void int04_confirmarDosVecesConLaMismaClaveNoConfirmaDosVeces() {
        long turno = publicar();
        con(tokenPaciente).post("/turnos/" + turno + "/reservar").then().statusCode(200);
        String clave = UUID.randomUUID().toString();

        Response primera = con(tokenPaciente).header("Idempotency-Key", clave).post("/turnos/" + turno + "/confirmar?particular=true");
        assertEquals(200, primera.statusCode());
        assertEquals("CONFIRMADO", primera.path("estado"));

        // El mismo pedido otra vez (reintento): misma respuesta, sin el 409 de "ya confirmado".
        Response repetida = con(tokenPaciente).header("Idempotency-Key", clave).post("/turnos/" + turno + "/confirmar?particular=true");
        assertEquals(200, repetida.statusCode());
        assertEquals("true", repetida.header("Idempotent-Replayed"));
        assertEquals(primera.jsonPath().getMap("$"), repetida.jsonPath().getMap("$"));

        // La misma clave en otra operacion: 422, sin ejecutar nada.
        long otro = publicar();
        con(tokenPaciente).post("/turnos/" + otro + "/reservar").then().statusCode(200);
        Response otraOperacion = con(tokenPaciente).header("Idempotency-Key", clave).post("/turnos/" + otro + "/confirmar?particular=true");
        assertEquals(422, otraOperacion.statusCode());
        assertEquals("RESERVADO_TEMPORAL", con(tokenPaciente).get("/turnos/" + otro).path("estado"));
    }

    // ------------------------------------------------------------------ XXE

    @Test
    void int05_elServicioSoapRechazaXmlConEntidadesExternas() {
        String xxe = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE x [ <!ENTITY xxe SYSTEM \"file:///etc/passwd\"> ]>"
                + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ws=\"http://legado.obrassociales.ar/ws\">"
                + "<soapenv:Body><ws:consultarCobertura><obraSocial>OSMED</obraSocial><numeroAfiliado>&xxe;</numeroAfiliado>"
                + "</ws:consultarCobertura></soapenv:Body></soapenv:Envelope>";

        Response r = given().baseUri(urlSoap()).contentType("text/xml; charset=utf-8").header("SOAPAction", "\"\"")
                .body(xxe).post();

        assertEquals(500, r.statusCode(), "SOAP 1.1 responde los Fault con 500");
        String cuerpo = r.asString();
        assertTrue(cuerpo.contains("<faultcode>soap:Client</faultcode>"), cuerpo);
        assertTrue(cuerpo.contains("XXE"), cuerpo);
        assertFalse(cuerpo.contains("root:"), "La respuesta no puede traer el contenido del archivo");
        assertFalse(cuerpo.contains("XMLStreamReader"), "El Fault no expone detalles del parser");
    }

    @Test
    void int05b_unMensajeSoapNormalSigueFuncionando() {
        String normal = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ws=\"http://legado.obrassociales.ar/ws\">"
                + "<soapenv:Body><ws:listarObrasSociales/></soapenv:Body></soapenv:Envelope>";
        Response r = given().baseUri(urlSoap()).contentType("text/xml; charset=utf-8").header("SOAPAction", "\"\"")
                .body(normal).post();
        assertEquals(200, r.statusCode());
        assertTrue(r.asString().contains("OSMED"));
    }

    // ------------------------------------------------------------------ helpers

    /** El simulador del legado corre en el mismo WildFly que la API. */
    private static String urlSoap() {
        return System.getProperty("api.base").replace("/mediconecta/api", "/obras-sociales-mock/ObrasSociales");
    }

    private long publicar() {
        return ((Number) con(tokenProfesional).body(json(Map.of("fechaHora", fecha(30))))
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
        return LocalDateTime.now().plusDays(dias + n / 10).withHour(8 + n % 10).withMinute(0).withSecond(0).withNano(0).format(ISO);
    }

    private RequestSpecification con(String token) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + token);
    }

    private String login(String email, String password) {
        return given().contentType(ContentType.JSON).body(json(Map.of("email", email, "password", password)))
                .post("/usuarios/login").then().statusCode(200).extract().path("token");
    }

    private static String json(Map<String, ?> m) {
        StringBuilder sb = new StringBuilder("{");
        m.forEach((k, v) -> sb.append(sb.length() > 1 ? "," : "").append('"').append(k).append("\":")
                .append(v instanceof String ? "\"" + v + "\"" : String.valueOf(v)));
        return sb.append('}').toString();
    }

    private static String requerida(String propiedad) {
        String v = System.getProperty(propiedad);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Falta -D" + propiedad);
        }
        return v;
    }
}
