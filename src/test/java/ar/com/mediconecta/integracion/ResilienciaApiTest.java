package ar.com.mediconecta.integracion;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Resiliencia ante fallas de la mensajería y de la plataforma de videollamadas (MED-69), contra
 * el sistema levantado con Docker Compose. Se apaga y se prende el broker de verdad y la
 * plataforma de videollamadas se hace fallar con videollamadas.mock.fallar (jboss-cli).
 *
 * El broker reintenta cada mensaje 3 veces, con 2 s entre intentos, y después lo manda a la DLQ
 * (docker/artemis/broker.xml). WildFly se reconecta solo cuando el broker vuelve.
 *
 * Corre con -Dapi.base=... y necesita el comando docker (como en el CI).
 */
@EnabledIfSystemProperty(named = "api.base", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class ResilienciaApiTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final AtomicInteger HORA = new AtomicInteger();

    private String tokenPaciente;
    private String tokenProfesional;

    @BeforeAll
    void preparar() throws Exception {
        Assumptions.assumeTrue(Docker.ejecutar("version").ok(), "Hace falta el comando docker para simular las fallas");
        RestAssured.baseURI = System.getProperty("api.base");
        String sufijo = String.valueOf(System.currentTimeMillis());
        String tokenAdmin = login(requerida("admin.email"), requerida("admin.password"));
        String emailProf = "res_prof_" + sufijo + "@test.local";
        con(tokenAdmin).body(Map.of("nombre", "Profesional Resiliencia", "email", emailProf, "passwordTemporal", "Temp1234"))
                .post("/admin/profesionales").then().statusCode(200);
        tokenProfesional = con(login(emailProf, "Temp1234"))
                .body(Map.of("passwordActual", "Temp1234", "passwordNueva", "Prueba1234"))
                .post("/usuarios/cambiar-password").then().statusCode(200).extract().path("token");
        tokenPaciente = given().contentType(ContentType.JSON)
                .body(Map.of("nombre", "Paciente Resiliencia", "email", "res_pac_" + sufijo + "@test.local", "password", "Prueba1234"))
                .post("/usuarios").then().statusCode(200).extract().path("token");
    }

    // ---------------------------------------------------------------- broker apagado

    @Test
    void r01_conElBrokerApagadoNoSeConfirmaNadaYCuandoVuelveSeConfirmaSinReiniciarLaApp() throws Exception {
        long turno = publicar("PRESENCIAL");
        con(tokenPaciente).post("/turnos/" + turno + "/reservar").then().statusCode(200);

        apagarBroker();
        try {
            long inicio = System.currentTimeMillis();
            Response r = con(tokenPaciente).post("/turnos/" + turno + "/confirmar?particular=true");
            long ms = System.currentTimeMillis() - inicio;
            assertEquals(503, r.statusCode(), r.asString());
            assertTrue(r.contentType().startsWith("application/problem+json"));
            assertTrue(r.asString().contains("mensajería"), r.asString());
            assertTrue(ms <= 5_000, "la respuesta tardó " + ms + " ms (la HU-04 pide 5 s como máximo)");
            assertEquals("RESERVADO_TEMPORAL", estado(turno), "no se guardó ningún cambio");
        } finally {
            prenderBroker();
        }

        // Sin reiniciar WildFly: se reconecta solo y la misma reserva se confirma.
        esperar(60, () -> con(tokenPaciente).post("/turnos/" + turno + "/confirmar?particular=true").statusCode() == 200,
                "no se pudo confirmar después de que volvió el broker");
        assertEquals("CONFIRMADO", estado(turno));
        // El aviso viaja por el tópico: que llegue prueba que la mensajería volvió a funcionar.
        esperar(30, () -> titulosDeNotificaciones().contains("Turno confirmado"), "no llegó el aviso de turno confirmado");
    }

    // ---------------------------------------------------------------- videollamadas

    @Test
    void r02_siLaPlataformaDeVideollamadasFallaYVuelveDentroDeLosReintentosLaSalaSeCrea() throws Exception {
        long turno = turnoVirtualReservado();
        // La plataforma falla los 2 primeros pedidos (intentos 1 y 2) y responde en el tercero.
        Docker.propiedad("videollamadas.mock.fallarPedidos", "2");
        try {
            con(tokenPaciente).post("/turnos/" + turno + "/confirmar?particular=true").then().statusCode(200);
            assertEquals("CONFIRMADO", estado(turno), "el turno se confirma aunque la plataforma falle");
            esperar(20, () -> con(tokenPaciente).get("/turnos/" + turno + "/sala").statusCode() == 200,
                    "la sala no se creó en los reintentos");
        } finally {
            Docker.sinPropiedad("videollamadas.mock.fallarPedidos");
        }
    }

    @Test
    void r03_siLaPlataformaFallaLosTresIntentosElMensajeQuedaEnLaDlqYElTurnoSigueConfirmado() throws Exception {
        long antes = mensajesEnLaDlq();
        long turno = turnoVirtualReservado();
        Docker.propiedad("videollamadas.mock.fallar", "true");
        try {
            con(tokenPaciente).post("/turnos/" + turno + "/confirmar?particular=true").then().statusCode(200);
            esperar(30, () -> mensajesEnLaDlq() > antes, "el mensaje no llegó a la DLQ");
        } finally {
            Docker.sinPropiedad("videollamadas.mock.fallar");
        }
        assertEquals("CONFIRMADO", estado(turno));
        assertEquals(404, con(tokenPaciente).get("/turnos/" + turno + "/sala").statusCode(), "sin sala hasta reprocesar la DLQ");
    }

    @Test
    void r04_losMensajesDeLaDlqNoSePierdenAlReiniciarElBroker() throws Exception {
        long antes = mensajesEnLaDlq();
        assertTrue(antes > 0, "r03 deja al menos un mensaje en la DLQ");
        apagarBroker();
        prenderBroker();
        assertEquals(antes, mensajesEnLaDlq(), "el journal del broker conserva los mensajes");
    }

    /** Deja el sistema andando para las pruebas que siguen: el broker prendido y la app reconectada. */
    @AfterAll
    void dejarElSistemaAndando() throws Exception {
        if (!Docker.ejecutar("compose", "ps", "--format", "{{.State}}", "broker").salida().contains("running")) {
            prenderBroker();
        }
        long turno = publicar("PRESENCIAL");
        con(tokenPaciente).post("/turnos/" + turno + "/reservar").then().statusCode(200);
        esperar(60, () -> con(tokenPaciente).post("/turnos/" + turno + "/confirmar?particular=true").statusCode() == 200,
                "la app no volvió a confirmar turnos después de las pruebas del broker");
    }

    // ---------------------------------------------------------------- ayudas

    private void apagarBroker() throws Exception {
        assertTrue(Docker.ejecutar("compose", "stop", "broker").ok(), "no se pudo apagar el broker");
    }

    private void prenderBroker() throws Exception {
        assertTrue(Docker.ejecutar("compose", "start", "broker").ok(), "no se pudo prender el broker");
        esperar(120, () -> {
            try {
                return Docker.ejecutar("compose", "ps", "--format", "{{.Health}}", "broker").salida().contains("healthy")
                        && !Docker.ejecutar("compose", "ps", "--format", "{{.Health}}", "broker").salida().contains("unhealthy");
            } catch (Exception e) {
                return false;
            }
        }, "el broker no volvió a estar disponible");
    }

    /** Mensajes que hay ahora en la cola de mensajes muertos (consola de Artemis por línea de comandos). */
    private long mensajesEnLaDlq() {
        try {
            String usuario = Docker.variable("broker", "ARTEMIS_USER");
            String clave = Docker.variable("broker", "ARTEMIS_PASSWORD");
            Docker.Resultado r = Docker.ejecutar("compose", "exec", "-T", "broker", "/var/lib/artemis-instance/bin/artemis",
                    "queue", "stat", "--user", usuario, "--password", clave, "--queueName", "DLQ", "--silent");
            for (String linea : r.salida().split("\n")) {
                String[] c = linea.split("\\|");
                if (c.length > 4 && c[1].trim().equals("DLQ")) {
                    return Long.parseLong(c[4].trim());
                }
            }
            throw new IllegalStateException("No se pudo leer la DLQ: " + r.salida());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void esperar(int segundos, BooleanSupplier condicion, String mensaje) throws InterruptedException {
        long limite = System.currentTimeMillis() + segundos * 1000L;
        while (System.currentTimeMillis() < limite) {
            if (condicion.getAsBoolean()) {
                return;
            }
            Thread.sleep(1_000);
        }
        assertTrue(condicion.getAsBoolean(), mensaje + " (esperé " + segundos + " s)");
    }

    private List<String> titulosDeNotificaciones() {
        return con(tokenPaciente).get("/notificaciones").then().statusCode(200).extract().jsonPath().getList("titulo");
    }

    private String estado(long turno) {
        return con(tokenPaciente).get("/turnos/" + turno).path("estado");
    }

    private long turnoVirtualReservado() {
        long turno = publicar("VIRTUAL");
        con(tokenPaciente).post("/turnos/" + turno + "/reservar").then().statusCode(200);
        return turno;
    }

    private long publicar(String modalidad) {
        return ((Number) con(tokenProfesional).body(Map.of("fechaHora", fecha(), "modalidad", modalidad))
                .post("/turnos").then().statusCode(200).extract().path("id")).longValue();
    }

    /** Fechas lejanas y distintas en cada llamada para que no choquen con otras pruebas. */
    private static String fecha() {
        int n = HORA.incrementAndGet();
        return LocalDateTime.now().plusDays(90 + n / 10).withHour(8 + n % 10).withMinute(15).withSecond(0).withNano(0).format(ISO);
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
