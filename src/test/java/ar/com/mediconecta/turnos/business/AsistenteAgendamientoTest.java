package ar.com.mediconecta.turnos.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.turnos.model.EstadoTurno;
import ar.com.mediconecta.turnos.model.Turno;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** La conversacion @Stateful recuerda profesional y turno retenido entre pedidos. */
@ExtendWith(MockitoExtension.class)
class AsistenteAgendamientoTest {

    private static final Long PACIENTE = 1L;
    private static final Long OTRO_PACIENTE = 2L;
    private static final Long PROFESIONAL = 7L;

    @Mock
    private ITurnoService turnoService;

    @InjectMocks
    private AsistenteAgendamiento asistente;

    @BeforeEach
    void iniciar() {
        asistente.crear();
    }

    @Test
    void recuerdaElProfesionalElegido() {
        Turno libre = turno(50L, EstadoTurno.DISPONIBLE);
        when(turnoService.consultarDisponibilidad(PROFESIONAL)).thenReturn(List.of(libre));

        EstadoAsistente e = asistente.elegirProfesional(PACIENTE, PROFESIONAL);

        assertEquals(PROFESIONAL, e.profesionalId);
        assertEquals(List.of(libre), e.horarios);
        assertEquals(1, e.pedidosAtendidos);
        assertEquals(PROFESIONAL, asistente.estado(PACIENTE).profesionalId);
    }

    @Test
    void reservarRetieneElTurnoYSueltaElAnterior() {
        retener(50L);
        Turno segundo = retenido(60L);
        when(turnoService.reservarTemporalmente(PACIENTE, 60L)).thenReturn(segundo);
        when(turnoService.buscarTurno(60L)).thenReturn(segundo);

        EstadoAsistente e = asistente.reservar(PACIENTE, 60L);

        verify(turnoService).liberarReservaTemporal(PACIENTE, 50L);
        assertSame(segundo, e.turnoRetenido);
    }

    @Test
    void reservarElMismoTurnoNoLoSuelta() {
        retener(50L);
        retener(50L);
        verify(turnoService, never()).liberarReservaTemporal(PACIENTE, 50L);
    }

    @Test
    void confirmarTerminaLaConversacion() {
        retener(50L);
        Turno confirmado = turno(50L, EstadoTurno.CONFIRMADO);
        when(turnoService.confirmarTurno(50L, true)).thenReturn(confirmado);

        assertSame(confirmado, asistente.confirmar(PACIENTE, true));
    }

    @Test
    void confirmarSinTurnoRetenido() {
        assertThrows(ConflictoTurnoException.class, () -> asistente.confirmar(PACIENTE, false));
    }

    @Test
    void siLaReservaVencioAlConfirmarSeOlvida() {
        retener(50L);
        when(turnoService.confirmarTurno(50L, false)).thenThrow(new ConflictoTurnoException("vencio"));

        assertThrows(ConflictoTurnoException.class, () -> asistente.confirmar(PACIENTE, false));
        assertThrows(ConflictoTurnoException.class, () -> asistente.confirmar(PACIENTE, false));
    }

    @Test
    void abandonarLiberaElHorario() {
        retener(50L);
        asistente.abandonar(PACIENTE);
        verify(turnoService).liberarReservaTemporal(PACIENTE, 50L);
    }

    @Test
    void siLaReservaYaNoEsSuyaLaFotoLaSuelta() {
        retener(50L);
        Turno vencido = retenido(50L);
        vencido.setHoldExpiraEn(LocalDateTime.now().minusMinutes(1));
        when(turnoService.buscarTurno(50L)).thenReturn(vencido);

        assertNull(asistente.estado(PACIENTE).turnoRetenido);
        verify(turnoService).liberarReservaTemporal(PACIENTE, 50L);
    }

    @Test
    void otroUsuarioEnElMismoNavegadorEmpiezaDeCero() {
        retener(50L);
        EstadoAsistente e = asistente.estado(OTRO_PACIENTE);

        verify(turnoService).liberarReservaTemporal(PACIENTE, 50L);
        assertNull(e.profesionalId);
        assertNull(e.turnoRetenido);
    }

    @Test
    void sinPacienteNoAtiende() {
        assertThrows(DatosTurnoInvalidosException.class, () -> asistente.estado(null));
    }

    @Test
    void cicloDeVida() {
        asistente.pasivar();
        asistente.activar();
        asistente.destruir();
        assertEquals(0, asistente.estado(PACIENTE).pedidosAtendidos - 1);
    }

    private void retener(Long turnoId) {
        Turno t = retenido(turnoId);
        when(turnoService.reservarTemporalmente(PACIENTE, turnoId)).thenReturn(t);
        when(turnoService.buscarTurno(turnoId)).thenReturn(t);
        asistente.reservar(PACIENTE, turnoId);
    }

    private static Turno retenido(Long id) {
        Turno t = turno(id, EstadoTurno.RESERVADO_TEMPORAL);
        t.setPacienteId(PACIENTE);
        t.setHoldExpiraEn(LocalDateTime.now().plusMinutes(4));
        return t;
    }

    private static Turno turno(Long id, EstadoTurno estado) {
        Turno t = new Turno();
        t.setId(id);
        t.setProfesionalId(PROFESIONAL);
        t.setEstado(estado);
        t.setFechaHora(LocalDateTime.now().plusDays(2));
        return t;
    }
}
