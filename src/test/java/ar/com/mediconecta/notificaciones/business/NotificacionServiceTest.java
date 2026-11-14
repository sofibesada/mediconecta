package ar.com.mediconecta.notificaciones.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.notificaciones.data.NotificacionRepository;
import ar.com.mediconecta.notificaciones.model.Notificacion;
import ar.com.mediconecta.turnos.eventos.TipoEventoTurno;
import ar.com.mediconecta.turnos.eventos.TurnoEvento;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    private static final Long PACIENTE = 1L;
    private static final Long PROFESIONAL = 2L;

    @Mock private NotificacionRepository repository;
    @Mock private IUsuarioService usuarioService;

    @InjectMocks private NotificacionService service;

    private final List<Notificacion> guardadas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        lenient().when(usuarioService.nombreDe(PACIENTE)).thenReturn("Ana");
        lenient().when(usuarioService.nombreDe(PROFESIONAL)).thenReturn("Dr. Gomez");
        lenient().when(repository.guardar(any())).thenAnswer(i -> {
            guardadas.add(i.getArgument(0));
            return i.getArgument(0);
        });
    }

    @ParameterizedTest
    @EnumSource(TipoEventoTurno.class)
    void cadaEventoAvisaAlPacienteYAlProfesional(TipoEventoTurno tipo) {
        service.procesarEventoTurno(evento(tipo, "PRESENCIAL"));

        assertEquals(2, guardadas.size());
        assertEquals(PACIENTE, guardadas.get(0).getUsuarioId());
        assertEquals(PROFESIONAL, guardadas.get(1).getUsuarioId());
        assertEquals(tipo.name(), guardadas.get(0).getTipo());
        assertTrue(guardadas.get(0).getMensaje().contains("Dr. Gomez"));
        assertTrue(guardadas.get(1).getMensaje().contains("Ana"));
    }

    @Test
    void lasTeleconsultasIndicanComoEntrar() {
        service.procesarEventoTurno(evento(TipoEventoTurno.TURNO_CONFIRMADO, "VIRTUAL"));
        assertTrue(guardadas.get(0).getMensaje().contains("teleconsulta"));
        guardadas.clear();
        service.procesarEventoTurno(evento(TipoEventoTurno.TURNO_RECORDATORIO, "VIRTUAL"));
        assertTrue(guardadas.get(0).getMensaje().contains("Unirse a la videollamada"));
    }

    @Test
    void reprogramadoSinFechaAnteriorMuestraGuion() {
        TurnoEvento e = evento(TipoEventoTurno.TURNO_REPROGRAMADO, "PRESENCIAL");
        e.setFechaHoraAnterior(null);
        service.procesarEventoTurno(e);
        assertTrue(guardadas.get(0).getMensaje().contains("—"));
    }

    @Test
    void unEventoRepetidoNoDuplicaAvisos() {
        when(repository.existe(anyString(), anyLong())).thenReturn(true);
        service.procesarEventoTurno(evento(TipoEventoTurno.TURNO_CANCELADO, "PRESENCIAL"));
        verify(repository, never()).guardar(any());
    }

    @Test
    void unHorarioSinPacienteSoloAvisaAlProfesional() {
        TurnoEvento e = evento(TipoEventoTurno.TURNO_CANCELADO, "PRESENCIAL");
        e.setPacienteId(null);
        service.procesarEventoTurno(e);
        verify(repository, times(1)).guardar(any());
    }

    @Test
    void marcarLeidaSoloLasPropias() {
        Notificacion n = new Notificacion();
        n.setUsuarioId(PACIENTE);
        when(repository.buscarPorId(9L)).thenReturn(n);

        service.marcarLeida(PACIENTE, 9L);
        assertTrue(n.isLeida());
        assertThrows(IllegalArgumentException.class, () -> service.marcarLeida(PROFESIONAL, 9L));
        assertThrows(IllegalArgumentException.class, () -> service.marcarLeida(PACIENTE, 10L));
    }

    @Test
    void unAvisoSueltoNoTieneTurno() {
        service.avisar(1L, "RECUPERAR_PASSWORD", "Pedido", "Ana pidio recuperar su contrasena");

        assertEquals(1, guardadas.size());
        Notificacion n = guardadas.get(0);
        assertEquals(1L, n.getUsuarioId());
        assertEquals("RECUPERAR_PASSWORD", n.getTipo());
        assertEquals("Pedido", n.getTitulo());
        assertEquals(null, n.getTurnoId());
        org.junit.jupiter.api.Assertions.assertNotNull(n.getEventoId());
    }

    @Test
    void consultas() {
        when(repository.listarPorUsuario(PACIENTE, 50)).thenReturn(List.of(new Notificacion()));
        when(repository.contarNoLeidas(PACIENTE)).thenReturn(3L);

        assertEquals(1, service.listarDeUsuario(PACIENTE).size());
        assertEquals(3L, service.contarNoLeidas(PACIENTE));
        service.marcarTodasLeidas(PACIENTE);
        verify(repository).marcarTodasLeidas(PACIENTE);
    }

    private static TurnoEvento evento(TipoEventoTurno tipo, String modalidad) {
        TurnoEvento e = new TurnoEvento();
        e.setEventoId("ev-" + tipo);
        e.setTipo(tipo);
        e.setTurnoId(100L);
        e.setPacienteId(PACIENTE);
        e.setProfesionalId(PROFESIONAL);
        e.setModalidad(modalidad);
        e.setFechaHora(LocalDateTime.of(2026, 12, 1, 10, 0));
        e.setFechaHoraAnterior(LocalDateTime.of(2026, 11, 30, 9, 0));
        return e;
    }
}
