package ar.com.mediconecta.telemedicina.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.telemedicina.data.SalaVirtualRepository;
import ar.com.mediconecta.telemedicina.integracion.SalaExterna;
import ar.com.mediconecta.telemedicina.integracion.VideollamadasClient;
import ar.com.mediconecta.telemedicina.model.SalaVirtual;
import ar.com.mediconecta.turnos.eventos.TipoEventoTurno;
import ar.com.mediconecta.turnos.eventos.TurnoEvento;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemedicinaServiceTest {

    private static final Long TURNO = 100L;
    private static final Long PACIENTE = 1L;
    private static final Long PROFESIONAL = 2L;

    @Mock private SalaVirtualRepository repository;
    @Mock private VideollamadasClient videollamadas;

    @InjectMocks private TelemedicinaService service;

    @Test
    void unTurnoVirtualConfirmadoCreaSuSala() {
        when(videollamadas.crearSala(any(), any(), any())).thenReturn(externa());

        service.procesarEventoTurno(evento(TipoEventoTurno.TURNO_CONFIRMADO, "VIRTUAL"));

        ArgumentCaptor<SalaVirtual> captor = ArgumentCaptor.forClass(SalaVirtual.class);
        verify(repository).guardar(captor.capture());
        SalaVirtual s = captor.getValue();
        assertEquals("https://sala/invitado", s.urlPara(PACIENTE));
        assertEquals("https://sala/anfitrion", s.urlPara(PROFESIONAL));
        assertEquals(SalaVirtual.Estado.ACTIVA, s.getEstado());
    }

    @Test
    void unTurnoPresencialNoTieneSala() {
        service.procesarEventoTurno(evento(TipoEventoTurno.TURNO_CONFIRMADO, "PRESENCIAL"));
        verifyNoInteractions(repository, videollamadas);
    }

    @Test
    void unEventoRepetidoNoCreaOtraSala() {
        when(repository.buscarPorTurno(TURNO)).thenReturn(sala(SalaVirtual.Estado.ACTIVA));
        service.procesarEventoTurno(evento(TipoEventoTurno.TURNO_CONFIRMADO, "VIRTUAL"));
        verifyNoInteractions(videollamadas);
    }

    @Test
    void unaSalaEliminadaSeReactivaSinDuplicarla() {
        SalaVirtual vieja = sala(SalaVirtual.Estado.ELIMINADA);
        when(repository.buscarPorTurno(TURNO)).thenReturn(vieja);
        when(videollamadas.crearSala(any(), any(), any())).thenReturn(externa());

        service.procesarEventoTurno(evento(TipoEventoTurno.TURNO_CONFIRMADO, "VIRTUAL"));

        assertEquals(SalaVirtual.Estado.ACTIVA, vieja.getEstado());
        verify(repository, never()).guardar(any());
    }

    @Test
    void alCancelarSeEliminaLaSala() {
        SalaVirtual s = sala(SalaVirtual.Estado.ACTIVA);
        when(repository.buscarPorTurno(TURNO)).thenReturn(s);

        service.procesarEventoTurno(evento(TipoEventoTurno.TURNO_CANCELADO, "VIRTUAL"));

        assertEquals(SalaVirtual.Estado.ELIMINADA, s.getEstado());
        verify(videollamadas).eliminarSala("ext-1");
    }

    @Test
    void cancelarSinSalaOYaEliminadaNoLlamaAlProveedor() {
        when(repository.buscarPorTurno(TURNO)).thenReturn(null);
        service.procesarEventoTurno(evento(TipoEventoTurno.TURNO_CANCELADO, "VIRTUAL"));
        when(repository.buscarPorTurno(TURNO)).thenReturn(sala(SalaVirtual.Estado.ELIMINADA));
        service.procesarEventoTurno(evento(TipoEventoTurno.TURNO_CANCELADO, "VIRTUAL"));
        service.procesarEventoTurno(evento(TipoEventoTurno.TURNO_REPROGRAMADO, "VIRTUAL"));
        verifyNoInteractions(videollamadas);
    }

    @Test
    void soloLosParticipantesVenLaSala() {
        SalaVirtual s = sala(SalaVirtual.Estado.ACTIVA);
        when(repository.buscarPorTurno(TURNO)).thenReturn(s);

        assertSame(s, service.obtenerSala(TURNO, PACIENTE));
        assertSame(s, service.obtenerSala(TURNO, PROFESIONAL));
        assertThrows(SalaNoDisponibleException.class, () -> service.obtenerSala(TURNO, 99L));
        assertThrows(SalaNoDisponibleException.class, () -> service.obtenerSala(TURNO, null));

        s.setEstado(SalaVirtual.Estado.ELIMINADA);
        assertThrows(SalaNoDisponibleException.class, () -> service.obtenerSala(TURNO, PACIENTE));
        when(repository.buscarPorTurno(TURNO)).thenReturn(null);
        assertThrows(SalaNoDisponibleException.class, () -> service.obtenerSala(TURNO, PACIENTE));
    }

    private static SalaVirtual sala(SalaVirtual.Estado estado) {
        SalaVirtual s = new SalaVirtual();
        s.setTurnoId(TURNO);
        s.setPacienteId(PACIENTE);
        s.setProfesionalId(PROFESIONAL);
        s.setSalaExternaId("ext-1");
        s.setEstado(estado);
        return s;
    }

    private static SalaExterna externa() {
        SalaExterna e = new SalaExterna();
        e.id = "ext-1";
        e.joinUrl = "https://sala/invitado";
        e.hostUrl = "https://sala/anfitrion";
        return e;
    }

    private static TurnoEvento evento(TipoEventoTurno tipo, String modalidad) {
        TurnoEvento e = new TurnoEvento();
        e.setEventoId("ev-1");
        e.setTipo(tipo);
        e.setTurnoId(TURNO);
        e.setPacienteId(PACIENTE);
        e.setProfesionalId(PROFESIONAL);
        e.setModalidad(modalidad);
        e.setFechaHora(LocalDateTime.now().plusDays(1));
        return e;
    }
}
