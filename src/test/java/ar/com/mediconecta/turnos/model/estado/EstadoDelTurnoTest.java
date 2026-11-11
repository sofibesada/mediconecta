package ar.com.mediconecta.turnos.model.estado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ar.com.mediconecta.turnos.model.EstadoTurno;
import ar.com.mediconecta.turnos.model.Turno;
import java.time.LocalDateTime;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tabla de transiciones del patron State. Cada fila: estado de partida, operacion y si es
 * valida. Las invalidas lanzan TransicionInvalidaException (409) y no cambian el turno.
 */
class EstadoDelTurnoTest {

    private static final Predicate<Turno> SIEMPRE = t -> true;
    private static final Predicate<Turno> NUNCA = t -> false;

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @CsvSource({
            "DISPONIBLE,         reservar,    RESERVADO_TEMPORAL",
            "DISPONIBLE,         confirmar,   409",
            "DISPONIBLE,         liberar,     409",
            "DISPONIBLE,         cancelar,    CANCELADO",
            "DISPONIBLE,         reprogramar, DISPONIBLE",
            "RESERVADO_TEMPORAL, reservar,    409",
            "RESERVADO_TEMPORAL, confirmar,   CONFIRMADO",
            "RESERVADO_TEMPORAL, liberar,     DISPONIBLE",
            "RESERVADO_TEMPORAL, cancelar,    CANCELADO",
            "RESERVADO_TEMPORAL, reprogramar, 409",
            "CONFIRMADO,         reservar,    409",
            "CONFIRMADO,         confirmar,   409",
            "CONFIRMADO,         liberar,     409",
            "CONFIRMADO,         cancelar,    CANCELADO",
            "CONFIRMADO,         reprogramar, CONFIRMADO",
            "CANCELADO,          reservar,    409",
            "CANCELADO,          confirmar,   409",
            "CANCELADO,          liberar,     409",
            "CANCELADO,          cancelar,    409",
            "CANCELADO,          reprogramar, 409",
    })
    void tablaDeTransiciones(EstadoTurno desde, String operacion, String resultado) {
        Turno t = turno(desde, 72);
        Executable accion = switch (operacion) {
            case "reservar" -> () -> t.reservar(1L, LocalDateTime.now().plusMinutes(5));
            case "confirmar" -> t::confirmar;
            case "liberar" -> t::liberar;
            case "cancelar" -> () -> t.cancelar(SIEMPRE);
            default -> () -> t.reprogramar(LocalDateTime.now().plusDays(10));
        };
        if (resultado.equals("409")) {
            assertThrows(TransicionInvalidaException.class, accion);
            assertEquals(desde, t.getEstado(), "una transicion invalida no cambia el estado");
        } else {
            try {
                accion.execute();
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
            assertEquals(EstadoTurno.valueOf(resultado), t.getEstado());
        }
    }

    @Test
    void reservarDejaPacienteYPlazo() {
        Turno t = turno(EstadoTurno.DISPONIBLE, 72);
        LocalDateTime hasta = LocalDateTime.now().plusMinutes(5);
        t.reservar(9L, hasta);
        assertEquals(9L, t.getPacienteId());
        assertEquals(hasta, t.getHoldExpiraEn());
    }

    @Test
    void unaReservaVencidaSePuedeVolverAReservar() {
        Turno t = turno(EstadoTurno.RESERVADO_TEMPORAL, 72);
        t.setPacienteId(1L);
        t.setHoldExpiraEn(LocalDateTime.now().minusMinutes(1));

        t.reservar(2L, LocalDateTime.now().plusMinutes(5));

        assertEquals(2L, t.getPacienteId());
        assertEquals(EstadoTurno.RESERVADO_TEMPORAL, t.getEstado());
    }

    @Test
    void conLaReservaVencidaNoSeConfirma() {
        Turno t = turno(EstadoTurno.RESERVADO_TEMPORAL, 72);
        t.setHoldExpiraEn(LocalDateTime.now().minusSeconds(1));
        TransicionInvalidaException e = assertThrows(TransicionInvalidaException.class, t::confirmar);
        assertEquals("El hold del turno expiró, volvé a reservarlo", e.getMessage());
    }

    @Test
    void liberarBorraPacienteYPlazo() {
        Turno t = turno(EstadoTurno.RESERVADO_TEMPORAL, 72);
        t.setPacienteId(1L);
        t.liberar();
        assertNull(t.getPacienteId());
        assertNull(t.getHoldExpiraEn());
    }

    @Test
    void unHorarioPasadoNoSeReserva() {
        Turno t = turno(EstadoTurno.DISPONIBLE, -1);
        assertEquals("Ese horario ya pasó",
                assertThrows(TransicionInvalidaException.class, () -> t.reservar(1L, LocalDateTime.now())).getMessage());
    }

    @Test
    void unTurnoFinalizadoNoSeCancelaNiSeMueve() {
        Turno t = turno(EstadoTurno.CONFIRMADO, -1);
        assertEquals("No se puede cancelar: el turno ya finalizó",
                assertThrows(TransicionInvalidaException.class, () -> t.cancelar(SIEMPRE)).getMessage());
        assertEquals("No se puede reprogramar un turno que ya finalizó",
                assertThrows(TransicionInvalidaException.class, () -> t.reprogramar(LocalDateTime.now().plusDays(1))).getMessage());
    }

    @Test
    void laPoliticaDeAnticipacionPuedeImpedirLaCancelacion() {
        for (EstadoTurno e : new EstadoTurno[] {EstadoTurno.DISPONIBLE, EstadoTurno.RESERVADO_TEMPORAL, EstadoTurno.CONFIRMADO}) {
            Turno t = turno(e, 5);
            assertEquals("No se puede cancelar: faltan menos de 24hs para el turno",
                    assertThrows(TransicionInvalidaException.class, () -> t.cancelar(NUNCA)).getMessage());
            assertEquals(e, t.getEstado());
        }
    }

    @Test
    void mensajesDeLasTransicionesInvalidas() {
        assertEquals("No se puede reprogramar un turno cancelado",
                assertThrows(TransicionInvalidaException.class, () -> turno(EstadoTurno.CANCELADO, 72).reprogramar(LocalDateTime.now())).getMessage());
        assertEquals("El paciente está confirmando este turno; esperá a que lo confirme o se libere",
                assertThrows(TransicionInvalidaException.class, () -> turno(EstadoTurno.RESERVADO_TEMPORAL, 72).reprogramar(LocalDateTime.now())).getMessage());
        assertEquals("El turno no tiene una reserva temporal activa",
                assertThrows(TransicionInvalidaException.class, () -> turno(EstadoTurno.CONFIRMADO, 72).confirmar()).getMessage());
        assertEquals(EstadoTurno.CANCELADO, turno(EstadoTurno.CANCELADO, 1).estadoActual().nombre());
    }

    private static Turno turno(EstadoTurno estado, long horas) {
        Turno t = new Turno();
        t.setEstado(estado);
        t.setFechaHora(LocalDateTime.now().plusHours(horas));
        if (estado == EstadoTurno.RESERVADO_TEMPORAL) {
            t.setPacienteId(1L);
            t.setHoldExpiraEn(LocalDateTime.now().plusMinutes(3));
        }
        return t;
    }
}
