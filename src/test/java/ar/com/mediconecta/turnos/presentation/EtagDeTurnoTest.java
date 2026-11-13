package ar.com.mediconecta.turnos.presentation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.com.mediconecta.config.ApiError;
import ar.com.mediconecta.turnos.model.Turno;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

class EtagDeTurnoTest {

    private static Turno turno(Long version) {
        Turno t = new Turno();
        t.setVersion(version);
        return t;
    }

    @Test
    void elEtagEsLaVersionEntreComillas() {
        assertEquals("\"4\"", EtagDeTurno.de(turno(4L)).toString());
        assertEquals("\"0\"", EtagDeTurno.de(turno(null)).toString());
    }

    @Test
    void sinIfMatchSeHaceComoAntes() {
        assertDoesNotThrow(() -> EtagDeTurno.exigirIfMatch(null, turno(4L)));
        assertDoesNotThrow(() -> EtagDeTurno.exigirIfMatch(" ", turno(4L)));
        assertDoesNotThrow(() -> EtagDeTurno.exigirIfMatch("\"1\"", null));
    }

    @Test
    void conLaVersionActualSePuedeModificar() {
        assertDoesNotThrow(() -> EtagDeTurno.exigirIfMatch("\"4\"", turno(4L)));
        assertDoesNotThrow(() -> EtagDeTurno.exigirIfMatch("W/\"4\"", turno(4L)));
        assertDoesNotThrow(() -> EtagDeTurno.exigirIfMatch("\"2\", \"4\"", turno(4L)));
        assertDoesNotThrow(() -> EtagDeTurno.exigirIfMatch("*", turno(4L)));
    }

    @Test
    void conUnaVersionViejaResponde412() {
        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> EtagDeTurno.exigirIfMatch("\"3\"", turno(4L)));
        assertEquals(412, e.getResponse().getStatus());
        assertEquals(ApiError.PROBLEM_JSON_TYPE, e.getResponse().getMediaType());
        assertEquals(EtagDeTurno.CAMBIO, ((ApiError) e.getResponse().getEntity()).detail);
    }

    @Test
    void ifNoneMatchIndicaSiElClienteYaTieneLaVersion() {
        assertTrue(EtagDeTurno.noCambio("\"4\"", turno(4L)));
        assertTrue(EtagDeTurno.noCambio("W/\"4\"", turno(4L)));
        assertTrue(EtagDeTurno.noCambio("*", turno(4L)));
        assertFalse(EtagDeTurno.noCambio("\"3\"", turno(4L)));
        assertFalse(EtagDeTurno.noCambio(null, turno(4L)));
        assertFalse(EtagDeTurno.noCambio("", turno(4L)));
    }
}
