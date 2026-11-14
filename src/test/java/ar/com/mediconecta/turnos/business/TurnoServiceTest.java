package ar.com.mediconecta.turnos.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.facturacion.business.IFacturacionService;
import ar.com.mediconecta.obrassociales.business.AuditoriaAutorizacionService;
import ar.com.mediconecta.obrassociales.business.AutorizacionRechazadaException;
import ar.com.mediconecta.obrassociales.business.IObraSocialService;
import ar.com.mediconecta.obrassociales.business.IObraSocialService.ResultadoAutorizacion;
import ar.com.mediconecta.obrassociales.integracion.ObrasSocialesNoDisponibleException;
import ar.com.mediconecta.obrassociales.model.IntentoAutorizacion.Resultado;
import ar.com.mediconecta.turnos.data.TurnoRepository;
import ar.com.mediconecta.turnos.eventos.TipoEventoTurno;
import ar.com.mediconecta.turnos.eventos.TurnoEventoPublisher;
import ar.com.mediconecta.turnos.model.EstadoTurno;
import ar.com.mediconecta.turnos.model.Modalidad;
import ar.com.mediconecta.turnos.model.Turno;
import ar.com.mediconecta.turnos.model.estado.TransicionInvalidaException;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import ar.com.mediconecta.usuarios.model.Usuario;
import jakarta.ejb.EJBException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TurnoServiceTest {

    private static final Long PACIENTE = 10L;
    private static final Long PROFESIONAL = 20L;
    private static final Long TURNO = 100L;

    @Mock private TurnoRepository turnoRepository;
    @Mock private IUsuarioService usuarioService;
    @Mock private TurnoEventoPublisher eventoPublisher;
    @Mock private IObraSocialService obraSocialService;
    @Mock private AuditoriaAutorizacionService auditoria;
    @Mock private IFacturacionService facturacionService;

    @InjectMocks
    private TurnoService service;

    @BeforeEach
    void guardarDevuelveLoMismo() {
        lenient().when(turnoRepository.actualizar(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(turnoRepository.guardar(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void limpiar() {
        System.clearProperty("mediconecta.consulta.valor");
    }

    // ------------------------------------------------------------------ consultas

    @Test
    void lasConsultasDelegan() {
        Turno t = turno(EstadoTurno.DISPONIBLE, 2);
        when(turnoRepository.buscarPorId(TURNO)).thenReturn(t);
        when(turnoRepository.listarDisponiblesPorProfesional(PROFESIONAL)).thenReturn(List.of(t));
        when(turnoRepository.listarPorPaciente(PACIENTE)).thenReturn(List.of());
        when(turnoRepository.listarPorProfesional(PROFESIONAL)).thenReturn(List.of(t, t));

        assertSame(t, service.buscarTurno(TURNO));
        assertEquals(1, service.consultarDisponibilidad(PROFESIONAL).size());
        assertEquals(0, service.listarTurnosDePaciente(PACIENTE).size());
        assertEquals(2, service.listarTurnosDeProfesional(PROFESIONAL).size());
        service.init();
        service.destroy();
    }

    // ------------------------------------------------------------------ reservar

    @Nested
    class Reservar {

        @Test
        void retieneElTurno5Minutos() {
            when(usuarioService.existeUsuario(PACIENTE)).thenReturn(true);
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(turno(EstadoTurno.DISPONIBLE, 2));

            Turno r = service.reservarTemporalmente(PACIENTE, TURNO);

            assertEquals(EstadoTurno.RESERVADO_TEMPORAL, r.getEstado());
            assertEquals(PACIENTE, r.getPacienteId());
            assertTrue(r.getHoldExpiraEn().isAfter(LocalDateTime.now().plusMinutes(4)));
        }

        @Test
        void unaReservaVencidaDeOtroCuentaComoDisponible() {
            Turno t = turno(EstadoTurno.RESERVADO_TEMPORAL, 2);
            t.setPacienteId(99L);
            t.setHoldExpiraEn(LocalDateTime.now().minusMinutes(1));
            when(usuarioService.existeUsuario(PACIENTE)).thenReturn(true);
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(t);

            assertEquals(PACIENTE, service.reservarTemporalmente(PACIENTE, TURNO).getPacienteId());
        }

        @Test
        void pacienteInexistente() {
            when(usuarioService.existeUsuario(PACIENTE)).thenReturn(false);
            assertThrows(DatosTurnoInvalidosException.class, () -> service.reservarTemporalmente(PACIENTE, TURNO));
        }

        @Test
        void turnoInexistenteOcupadoOPasado() {
            when(usuarioService.existeUsuario(PACIENTE)).thenReturn(true);
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(null);
            assertThrows(ConflictoTurnoException.class, () -> service.reservarTemporalmente(PACIENTE, TURNO));

            Turno retenido = turno(EstadoTurno.RESERVADO_TEMPORAL, 2);
            retenido.setHoldExpiraEn(LocalDateTime.now().plusMinutes(3));
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(retenido);
            assertThrows(TransicionInvalidaException.class, () -> service.reservarTemporalmente(PACIENTE, TURNO));

            Turno sinHold = turno(EstadoTurno.RESERVADO_TEMPORAL, 2);
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(sinHold);
            assertThrows(TransicionInvalidaException.class, () -> service.reservarTemporalmente(PACIENTE, TURNO));

            when(turnoRepository.buscarPorId(TURNO)).thenReturn(turno(EstadoTurno.CONFIRMADO, 2));
            assertThrows(TransicionInvalidaException.class, () -> service.reservarTemporalmente(PACIENTE, TURNO));

            when(turnoRepository.buscarPorId(TURNO)).thenReturn(turno(EstadoTurno.DISPONIBLE, -1));
            TransicionInvalidaException e = assertThrows(TransicionInvalidaException.class, () -> service.reservarTemporalmente(PACIENTE, TURNO));
            assertEquals("Ese horario ya pasó", e.getMessage());
        }

        @Test
        void liberarSoloSuPropiaReserva() {
            Turno t = turno(EstadoTurno.RESERVADO_TEMPORAL, 2);
            t.setPacienteId(PACIENTE);
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(t);

            service.liberarReservaTemporal(PACIENTE, TURNO);

            assertEquals(EstadoTurno.DISPONIBLE, t.getEstado());
            assertNull(t.getPacienteId());
            assertNull(t.getHoldExpiraEn());
        }

        @Test
        void liberarNoTocaReservasAjenasNiConfirmadas() {
            Turno ajeno = turno(EstadoTurno.RESERVADO_TEMPORAL, 2);
            ajeno.setPacienteId(99L);
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(ajeno);
            service.liberarReservaTemporal(PACIENTE, TURNO);

            when(turnoRepository.buscarPorId(TURNO)).thenReturn(turno(EstadoTurno.CONFIRMADO, 2));
            service.liberarReservaTemporal(PACIENTE, TURNO);

            when(turnoRepository.buscarPorId(TURNO)).thenReturn(null);
            service.liberarReservaTemporal(PACIENTE, TURNO);

            assertEquals(EstadoTurno.RESERVADO_TEMPORAL, ajeno.getEstado());
            verify(turnoRepository, never()).actualizar(any());
        }
    }

    // ------------------------------------------------------------------ confirmar

    @Nested
    class Confirmar {

        private Turno reservado;
        private Usuario paciente;

        @BeforeEach
        void preparar() {
            reservado = turno(EstadoTurno.RESERVADO_TEMPORAL, 2);
            reservado.setPacienteId(PACIENTE);
            reservado.setHoldExpiraEn(LocalDateTime.now().plusMinutes(3));
            paciente = new Usuario();
            paciente.setId(PACIENTE);
            lenient().when(turnoRepository.buscarPorId(TURNO)).thenReturn(reservado);
            lenient().when(usuarioService.existeUsuario(PACIENTE)).thenReturn(true);
            lenient().when(usuarioService.buscarParaSesion(PACIENTE)).thenReturn(paciente);
        }

        @Test
        void comoParticularPagaElTotalYNoSeFactura() {
            Turno t = service.confirmarTurno(TURNO);

            assertEquals(EstadoTurno.CONFIRMADO, t.getEstado());
            assertEquals(new BigDecimal("20000"), t.getImporteCopago());
            assertNull(t.getImporteObraSocial());
            assertNull(t.getObraSocial());
            verify(eventoPublisher).publicar(TipoEventoTurno.TURNO_CONFIRMADO, t, null);
            verifyNoInteractions(obraSocialService, facturacionService);
        }

        @Test
        void conObraSocialPeroEligiendoParticular() {
            conObraSocial();
            Turno t = service.confirmarTurno(TURNO, true);

            assertNull(t.getCodigoAutorizacion());
            assertEquals(new BigDecimal("20000"), t.getImporteCopago());
            verifyNoInteractions(obraSocialService, facturacionService);
        }

        @Test
        void conObraSocialAutorizadaCalculaCopagoYFactura() {
            System.setProperty("mediconecta.consulta.valor", "30000");
            conObraSocial();
            when(obraSocialService.autorizarConsulta(eq("OSDE"), eq("12345678"), eq(TURNO), any()))
                    .thenReturn(new ResultadoAutorizacion(true, "AUT-1", null));

            Turno t = service.confirmarTurno(TURNO, false);

            assertEquals("OSDE", t.getObraSocial());
            assertEquals("AUT-1", t.getCodigoAutorizacion());
            assertEquals(new BigDecimal("9000.00"), t.getImporteCopago());
            assertEquals(new BigDecimal("21000.00"), t.getImporteObraSocial());
            verify(auditoria).registrar(TURNO, PACIENTE, "OSDE", Resultado.AUTORIZADA, "AUT-1", null);
            verify(eventoPublisher).publicar(TipoEventoTurno.TURNO_CONFIRMADO, t, null);
            verify(facturacionService).registrarPrestacion(TURNO, PACIENTE, "OSDE", "12345678", "AUT-1", new BigDecimal("21000.00"));
        }

        @Test
        void valorDeConsultaMalConfiguradoUsaElDeFabrica() {
            System.setProperty("mediconecta.consulta.valor", "no-es-un-numero");
            assertEquals(new BigDecimal("20000"), service.confirmarTurno(TURNO, false).getImporteCopago());
        }

        @Test
        void siLaObraSocialRechazaNoSeConfirmaNadaYQuedaAuditado() {
            conObraSocial();
            when(obraSocialService.autorizarConsulta(any(), any(), anyLong(), any()))
                    .thenReturn(new ResultadoAutorizacion(false, null, "Tope alcanzado"));

            AutorizacionRechazadaException e = assertThrows(AutorizacionRechazadaException.class, () -> service.confirmarTurno(TURNO, false));

            assertTrue(e.getMessage().contains("Tope alcanzado"));
            verify(auditoria).registrar(TURNO, PACIENTE, "OSDE", Resultado.RECHAZADA, null, "Tope alcanzado");
            verifyNoInteractions(eventoPublisher, facturacionService);
        }

        @Test
        void siLaObraSocialNoRespondeSeAuditaSinRespuesta() {
            conObraSocial();
            when(obraSocialService.autorizarConsulta(any(), any(), anyLong(), any()))
                    .thenThrow(new EJBException(new ObrasSocialesNoDisponibleException("timeout", null)));

            assertThrows(AutorizacionRechazadaException.class, () -> service.confirmarTurno(TURNO, false));

            verify(auditoria).registrar(eq(TURNO), eq(PACIENTE), eq("OSDE"), eq(Resultado.SIN_RESPUESTA), isNull(), any());
            verifyNoInteractions(eventoPublisher, facturacionService);
        }

        @Test
        void otraFallaTecnicaSePropaga() {
            conObraSocial();
            EJBException falla = new EJBException(new RuntimeException("otra cosa"));
            when(obraSocialService.autorizarConsulta(any(), any(), anyLong(), any())).thenThrow(falla);

            assertSame(falla, assertThrows(EJBException.class, () -> service.confirmarTurno(TURNO, false)));
            verifyNoInteractions(auditoria);
        }

        @Test
        void sinReservaActiva() {
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(null);
            assertThrows(ConflictoTurnoException.class, () -> service.confirmarTurno(TURNO, false));

            when(turnoRepository.buscarPorId(TURNO)).thenReturn(turno(EstadoTurno.DISPONIBLE, 2));
            assertThrows(TransicionInvalidaException.class, () -> service.confirmarTurno(TURNO, false));
        }

        @Test
        void pacienteDesactivado() {
            when(usuarioService.existeUsuario(PACIENTE)).thenReturn(false);
            assertThrows(ConflictoTurnoException.class, () -> service.confirmarTurno(TURNO, false));
        }

        @Test
        void holdVencidoLiberaElHorario() {
            reservado.setHoldExpiraEn(LocalDateTime.now().minusSeconds(1));

            assertThrows(ConflictoTurnoException.class, () -> service.confirmarTurno(TURNO, false));

            assertEquals(EstadoTurno.DISPONIBLE, reservado.getEstado());
            assertNull(reservado.getPacienteId());
            verify(turnoRepository).actualizar(reservado);
        }

        private void conObraSocial() {
            paciente.setObraSocial("OSDE");
            paciente.setNumeroAfiliado("12345678");
            paciente.setCopagoPorcentaje(30);
        }
    }

    // ------------------------------------------------------------------ cancelar

    @Nested
    class Cancelar {

        @Test
        void conMasDe24HorasSeCancelaYAvisa() {
            Turno t = turno(EstadoTurno.CONFIRMADO, 48);
            t.setPacienteId(PACIENTE);
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(t);

            service.cancelarTurno(TURNO);

            assertEquals(EstadoTurno.CANCELADO, t.getEstado());
            verify(eventoPublisher).publicar(TipoEventoTurno.TURNO_CANCELADO, t, null);
        }

        @Test
        void unHorarioLibreSeCancelaSinAvisar() {
            Turno t = turno(EstadoTurno.DISPONIBLE, 48);
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(t);

            service.cancelarTurno(TURNO);

            assertEquals(EstadoTurno.CANCELADO, t.getEstado());
            verifyNoInteractions(eventoPublisher);
        }

        @Test
        void conMenosDe24HorasNoSePuede() {
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(turno(EstadoTurno.CONFIRMADO, 5));
            TransicionInvalidaException e = assertThrows(TransicionInvalidaException.class, () -> service.cancelarTurno(TURNO));
            assertTrue(e.getMessage().contains("24hs"));
        }

        @Test
        void unaUrgenciaSePuedeCancelarSiempre() {
            Turno t = turno(EstadoTurno.CONFIRMADO, 1);
            t.setUrgente(true);
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(t);

            service.cancelarTurno(TURNO);

            assertEquals(EstadoTurno.CANCELADO, t.getEstado());
        }

        @Test
        void unTurnoFinalizadoNoSeCancela() {
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(turno(EstadoTurno.CONFIRMADO, -2));
            assertThrows(TransicionInvalidaException.class, () -> service.cancelarTurno(TURNO));
        }

        @Test
        void inexistenteOYaCanceladoNoHaceNada() {
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(null);
            service.cancelarTurno(TURNO);
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(turno(EstadoTurno.CANCELADO, 48));
            service.cancelarTurno(TURNO);
            verify(turnoRepository, never()).actualizar(any());
        }
    }

    // ------------------------------------------------------------------ reprogramar

    @Nested
    class Reprogramar {

        private final LocalDateTime nueva = LocalDateTime.now().plusDays(5).withNano(0);

        @Test
        void cambiaLaFechaYAvisaConLaAnterior() {
            Turno t = turno(EstadoTurno.CONFIRMADO, 48);
            t.setPacienteId(PACIENTE);
            LocalDateTime anterior = t.getFechaHora();
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(t);

            Turno r = service.reprogramarTurno(PROFESIONAL, TURNO, nueva);

            assertEquals(nueva, r.getFechaHora());
            verify(eventoPublisher).publicar(TipoEventoTurno.TURNO_REPROGRAMADO, r, anterior);
        }

        @Test
        void unHorarioLibreSeMueveSinAvisar() {
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(turno(EstadoTurno.DISPONIBLE, 48));
            service.reprogramarTurno(PROFESIONAL, TURNO, nueva);
            verifyNoInteractions(eventoPublisher);
        }

        @Test
        void validaciones() {
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(null);
            assertThrows(ConflictoTurnoException.class, () -> service.reprogramarTurno(PROFESIONAL, TURNO, nueva));

            when(turnoRepository.buscarPorId(TURNO)).thenReturn(turno(EstadoTurno.CONFIRMADO, 48));
            assertThrows(DatosTurnoInvalidosException.class, () -> service.reprogramarTurno(99L, TURNO, nueva));

            when(turnoRepository.buscarPorId(TURNO)).thenReturn(turno(EstadoTurno.CANCELADO, 48));
            assertThrows(TransicionInvalidaException.class, () -> service.reprogramarTurno(PROFESIONAL, TURNO, nueva));

            when(turnoRepository.buscarPorId(TURNO)).thenReturn(turno(EstadoTurno.CONFIRMADO, -3));
            assertThrows(TransicionInvalidaException.class, () -> service.reprogramarTurno(PROFESIONAL, TURNO, nueva));

            when(turnoRepository.buscarPorId(TURNO)).thenReturn(turno(EstadoTurno.RESERVADO_TEMPORAL, 48));
            assertThrows(TransicionInvalidaException.class, () -> service.reprogramarTurno(PROFESIONAL, TURNO, nueva));

            Turno t = turno(EstadoTurno.CONFIRMADO, 48);
            when(turnoRepository.buscarPorId(TURNO)).thenReturn(t);
            assertThrows(DatosTurnoInvalidosException.class, () -> service.reprogramarTurno(PROFESIONAL, TURNO, null));
            assertThrows(DatosTurnoInvalidosException.class, () -> service.reprogramarTurno(PROFESIONAL, TURNO, LocalDateTime.now().minusDays(1)));
            assertThrows(DatosTurnoInvalidosException.class, () -> service.reprogramarTurno(PROFESIONAL, TURNO, t.getFechaHora()));

            when(turnoRepository.existeTurnoEnHorario(PROFESIONAL, nueva, TURNO)).thenReturn(true);
            assertThrows(ConflictoTurnoException.class, () -> service.reprogramarTurno(PROFESIONAL, TURNO, nueva));
        }
    }

    // ------------------------------------------------------------------ crear

    @Nested
    class Crear {

        private final LocalDateTime fecha = LocalDateTime.now().plusDays(3).withNano(0);

        @Test
        void publicaUnHorarioPresencialPorDefecto() {
            when(usuarioService.esProfesional(PROFESIONAL)).thenReturn(true);

            Turno t = service.crearTurnoDisponible(PROFESIONAL, fecha, null);

            assertEquals(EstadoTurno.DISPONIBLE, t.getEstado());
            assertEquals(Modalidad.PRESENCIAL, t.getModalidad());
            assertEquals(PROFESIONAL, t.getProfesionalId());
        }

        @Test
        void publicaUnHorarioVirtual() {
            when(usuarioService.esProfesional(PROFESIONAL)).thenReturn(true);
            assertEquals(Modalidad.VIRTUAL, service.crearTurnoDisponible(PROFESIONAL, fecha, Modalidad.VIRTUAL).getModalidad());
        }

        @Test
        void validaciones() {
            when(usuarioService.esProfesional(PROFESIONAL)).thenReturn(false);
            assertThrows(DatosTurnoInvalidosException.class, () -> service.crearTurnoDisponible(PROFESIONAL, fecha, null));

            when(usuarioService.esProfesional(PROFESIONAL)).thenReturn(true);
            assertThrows(DatosTurnoInvalidosException.class, () -> service.crearTurnoDisponible(PROFESIONAL, null, null));
            assertThrows(DatosTurnoInvalidosException.class, () -> service.crearTurnoDisponible(PROFESIONAL, LocalDateTime.now().minusHours(1), null));

            when(turnoRepository.existeTurnoEnHorario(PROFESIONAL, fecha, 0L)).thenReturn(true);
            assertThrows(ConflictoTurnoException.class, () -> service.crearTurnoDisponible(PROFESIONAL, fecha, null));
        }
    }

    /** Turno del profesional de prueba, dentro de "horas" horas (negativo = ya paso). */
    static Turno turno(EstadoTurno estado, long horas) {
        Turno t = new Turno();
        t.setId(TURNO);
        t.setProfesionalId(PROFESIONAL);
        t.setEstado(estado);
        t.setFechaHora(LocalDateTime.now().plusHours(horas).withNano(0));
        return t;
    }
}
