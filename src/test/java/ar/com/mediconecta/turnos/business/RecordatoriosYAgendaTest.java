package ar.com.mediconecta.turnos.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.turnos.data.TurnoRepository;
import ar.com.mediconecta.turnos.eventos.TipoEventoTurno;
import ar.com.mediconecta.turnos.eventos.TurnoEventoPublisher;
import ar.com.mediconecta.turnos.model.EstadoTurno;
import ar.com.mediconecta.turnos.model.Turno;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordatoriosYAgendaTest {

    @Mock private TurnoRepository turnoRepository;
    @Mock private TurnoEventoPublisher eventoPublisher;
    @Mock private IUsuarioService usuarioService;
    @Mock private ITurnoService turnoService;

    @InjectMocks private RecordatorioTurnos recordatorios;
    @InjectMocks private AgendaTurnoFacade agenda;

    @AfterEach
    void limpiar() {
        System.clearProperty("mediconecta.recordatorio.horas");
    }

    @Test
    void cadaTurnoProximoRecibeUnRecordatorioYQuedaMarcado() {
        Turno t = TurnoServiceTest.turno(EstadoTurno.CONFIRMADO, 10);
        when(turnoRepository.listarParaRecordatorio(any(), any())).thenReturn(List.of(t));

        recordatorios.programarRecordatorios();

        assertEquals(t.getFechaHora(), t.getRecordatorioEnviadoPara());
        verify(turnoRepository).actualizar(t);
        verify(eventoPublisher).publicar(TipoEventoTurno.TURNO_RECORDATORIO, t, null);
    }

    @Test
    void laVentanaEsDe24HorasSalvoQueSeConfigureOtra() {
        when(turnoRepository.listarParaRecordatorio(any(), any())).thenReturn(List.of());
        recordatorios.programarRecordatorios();
        verify(turnoRepository).listarParaRecordatorio(any(), argThat(h -> h.isAfter(LocalDateTime.now().plusHours(23))));

        System.setProperty("mediconecta.recordatorio.horas", "2");
        recordatorios.programarRecordatorios();
        verify(turnoRepository).listarParaRecordatorio(any(), argThat(h -> h.isBefore(LocalDateTime.now().plusHours(3))));

        System.setProperty("mediconecta.recordatorio.horas", "mal");
        recordatorios.programarRecordatorios();
        verifyNoInteractions(eventoPublisher);
    }

    @Test
    void laAgendaSoloReservaParaPacientesActivos() {
        Turno t = new Turno();
        when(usuarioService.existeUsuario(1L)).thenReturn(true);
        when(turnoService.reservarTemporalmente(1L, 5L)).thenReturn(t);
        assertSame(t, agenda.agendarTurno(1L, 5L));

        when(usuarioService.existeUsuario(2L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> agenda.agendarTurno(2L, 5L));
        agenda.init();
        agenda.destroy();
    }

    @Test
    void politicasDeCancelacion() {
        assertTrue(new PoliticaCancelacionEstandar().puedeCancelarse(TurnoServiceTest.turno(EstadoTurno.CONFIRMADO, 25)));
        assertFalse(new PoliticaCancelacionEstandar().puedeCancelarse(TurnoServiceTest.turno(EstadoTurno.CONFIRMADO, 23)));
        assertTrue(new PoliticaCancelacionUrgencia().puedeCancelarse(TurnoServiceTest.turno(EstadoTurno.CONFIRMADO, 1)));
    }
}
