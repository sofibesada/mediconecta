package ar.com.mediconecta.pagos.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.pagos.data.PagoRepository;
import ar.com.mediconecta.pagos.integracion.PasarelaPagosClient;
import ar.com.mediconecta.pagos.model.Pago;
import ar.com.mediconecta.turnos.business.ITurnoService;
import ar.com.mediconecta.turnos.eventos.TipoEventoTurno;
import ar.com.mediconecta.turnos.eventos.TurnoEventoPublisher;
import ar.com.mediconecta.turnos.model.EstadoTurno;
import ar.com.mediconecta.turnos.model.Turno;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    private static final Long PACIENTE = 7L;
    private static final Long TURNO = 30L;
    private static final PagoService.DatosTarjeta VISA = new PagoService.DatosTarjeta("4111 1111 1111 1111", "12/30", "123", "ANA PEREZ");

    @Mock private PagoRepository repository;
    @Mock private PasarelaPagosClient pasarela;
    @Mock private ITurnoService turnoService;
    @Mock private TurnoEventoPublisher eventoPublisher;
    @InjectMocks private PagoService service;

    private Turno turno;

    @BeforeEach
    void preparar() {
        turno = new Turno();
        turno.setId(TURNO);
        turno.setPacienteId(PACIENTE);
        turno.setEstado(EstadoTurno.CONFIRMADO);
        turno.setImporteCopago(new BigDecimal("6000.00"));
        lenient().when(turnoService.buscarTurno(TURNO)).thenReturn(turno);
        lenient().when(repository.aprobadosDe(List.of(TURNO))).thenReturn(List.of());
    }

    private static PasarelaPagosClient.Resultado resultado(String estado, String motivo) {
        return new PasarelaPagosClient.Resultado("pay_1", estado, motivo, "Visa", "1111", "APROBADO".equals(estado) ? "123456" : null);
    }

    @Test
    void unPagoAprobadoSeGuardaSinElNumeroCompletoYAvisa() {
        when(pasarela.cobrar(eq(new BigDecimal("6000.00")), eq("turno-30"), eq("4111111111111111"), eq("12/30"), eq("123"), eq("ANA PEREZ"), eq("clave-1")))
                .thenReturn(resultado("APROBADO", null));

        Pago p = service.pagar(PACIENTE, TURNO, VISA, "clave-1");

        assertEquals(Pago.Estado.APROBADO, p.getEstado());
        assertEquals("1111", p.getUltimos4());
        assertEquals("Visa", p.getMarca());
        assertEquals("123456", p.getCodigoAutorizacion());
        assertEquals(new BigDecimal("6000.00"), p.getImporte());
        verify(repository).guardar(p);
        verify(eventoPublisher).publicar(TipoEventoTurno.PAGO_ACREDITADO, turno, null);
    }

    @Test
    void sinClaveSeGeneraUnaParaLaPasarela() {
        when(pasarela.cobrar(any(), anyString(), anyString(), anyString(), anyString(), anyString(), startsWith("turno-30-")))
                .thenReturn(resultado("APROBADO", null));
        service.pagar(PACIENTE, TURNO, VISA, null);
        service.pagar(PACIENTE, TURNO, VISA, " ");
        verify(eventoPublisher, org.mockito.Mockito.times(2)).publicar(eq(TipoEventoTurno.PAGO_ACREDITADO), any(), any());
    }

    @Test
    void unRechazoQuedaRegistradoYNoAvisa() {
        when(pasarela.cobrar(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(resultado("RECHAZADO", "Fondos insuficientes"));

        PagoRechazadoException e = assertThrows(PagoRechazadoException.class, () -> service.pagar(PACIENTE, TURNO, VISA, "c"));

        assertEquals("El pago fue rechazado: Fondos insuficientes", e.getMessage());
        ArgumentCaptor<Pago> guardado = ArgumentCaptor.forClass(Pago.class);
        verify(repository).guardar(guardado.capture());
        assertEquals(Pago.Estado.RECHAZADO, guardado.getValue().getEstado());
        assertEquals("Fondos insuficientes", guardado.getValue().getMotivo());
        verifyNoInteractions(eventoPublisher);
    }

    @Test
    void elRechazoSinMotivoSugiereConsultarAlBanco() {
        assertEquals("El pago fue rechazado: consultá con tu banco", new PagoRechazadoException(null).getMessage());
    }

    @Test
    void soloElPacienteDelTurnoPuedePagarlo() {
        assertThrows(IllegalArgumentException.class, () -> service.pagar(99L, TURNO, VISA, "c"));
        when(turnoService.buscarTurno(1L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.pagar(PACIENTE, 1L, VISA, "c"));
        verifyNoInteractions(pasarela);
    }

    @Test
    void soloSePaganTurnosConfirmadosConImporteYSinPagar() {
        turno.setEstado(EstadoTurno.RESERVADO_TEMPORAL);
        assertEquals("Solo se pueden pagar turnos confirmados",
                assertThrows(IllegalStateException.class, () -> service.pagar(PACIENTE, TURNO, VISA, "c")).getMessage());

        turno.setEstado(EstadoTurno.CONFIRMADO);
        turno.setImporteCopago(null);
        assertThrows(IllegalStateException.class, () -> service.pagar(PACIENTE, TURNO, VISA, "c"));
        turno.setImporteCopago(BigDecimal.ZERO);
        assertEquals("Este turno no tiene un importe a pagar",
                assertThrows(IllegalStateException.class, () -> service.pagar(PACIENTE, TURNO, VISA, "c")).getMessage());

        turno.setImporteCopago(new BigDecimal("6000"));
        when(repository.aprobadosDe(List.of(TURNO))).thenReturn(List.of(new Pago()));
        assertEquals("Este turno ya está pagado",
                assertThrows(IllegalStateException.class, () -> service.pagar(PACIENTE, TURNO, VISA, "c")).getMessage());
        verifyNoInteractions(pasarela);
    }

    @Test
    void unaTarjetaInvalidaNoLlegaALaPasarela() {
        assertThrows(IllegalArgumentException.class, () -> service.pagar(PACIENTE, TURNO, null, "c"));
        PagoService.DatosTarjeta mala = new PagoService.DatosTarjeta("4111 1111 1111 1112", "12/30", "123", "ANA");
        assertThrows(IllegalArgumentException.class, () -> service.pagar(PACIENTE, TURNO, mala, "c"));
        verifyNoInteractions(pasarela);
        verify(repository, never()).guardar(any());
    }

    @Test
    void consultas() {
        Pago a = new Pago();
        a.setTurnoId(1L);
        Pago b = new Pago();
        b.setTurnoId(2L);
        when(repository.aprobadosDe(List.of(1L, 2L))).thenReturn(List.of(a, b));
        when(repository.listarDeTurno(TURNO)).thenReturn(List.of(a));
        when(repository.listarRecientes(50)).thenReturn(List.of(b));

        Map<Long, Pago> porTurno = service.aprobadosDe(List.of(1L, 2L));
        assertSame(a, porTurno.get(1L));
        assertSame(b, porTurno.get(2L));
        assertEquals(List.of(a), service.deTurno(TURNO));
        assertTrue(service.listarRecientes().contains(b));
    }
}
