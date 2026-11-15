package ar.com.mediconecta.pagos.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntentosPagoTest {

    private static final Long TURNO = 30L;
    private static final PasarelaPagosClient.Resultado APROBADO =
            new PasarelaPagosClient.Resultado("pay_tarde", "APROBADO", null, "Visa", "1111", "777777");

    @Mock private PagoRepository repository;
    @Mock private PasarelaPagosClient pasarela;
    @Mock private ITurnoService turnoService;
    @Mock private TurnoEventoPublisher eventoPublisher;
    @InjectMocks private IntentosPago intentos;

    private Pago pendiente;
    private Turno turno;

    @BeforeEach
    void preparar() {
        pendiente = new Pago();
        pendiente.setTurnoId(TURNO);
        pendiente.setEstado(Pago.Estado.PENDIENTE);
        pendiente.setCreadoEn(LocalDateTime.now());
        turno = new Turno();
        turno.setId(TURNO);
        turno.setEstado(EstadoTurno.CONFIRMADO);
        lenient().when(repository.buscarPorId(1L)).thenReturn(pendiente);
        lenient().when(turnoService.buscarTurno(TURNO)).thenReturn(turno);
    }

    @AfterEach
    void limpiar() {
        System.clearProperty("mediconecta.pagos.ventanaSegundos");
    }

    @Test
    void registraElIntentoComoPendienteAntesDeCobrar() {
        when(repository.guardar(any())).thenAnswer(i -> i.getArgument(0));
        intentos.registrarPendiente(TURNO, 7L, new BigDecimal("6000"), "Visa", "1111");

        ArgumentCaptor<Pago> p = ArgumentCaptor.forClass(Pago.class);
        verify(repository).guardar(p.capture());
        assertEquals(Pago.Estado.PENDIENTE, p.getValue().getEstado());
        assertEquals("1111", p.getValue().getUltimos4());
        assertNotNull(p.getValue().getCreadoEn());
    }

    @Test
    void unCobroQueLaPasarelaAproboTardeQuedaAprobadoYSeAvisa() {
        when(pasarela.aprobadoDe("turno-30")).thenReturn(Optional.of(APROBADO));

        assertEquals(Pago.Estado.APROBADO, intentos.conciliar(1L));

        assertEquals("pay_tarde", pendiente.getIdExterno());
        assertEquals("777777", pendiente.getCodigoAutorizacion());
        verify(eventoPublisher).publicar(TipoEventoTurno.PAGO_ACREDITADO, turno, null);
        verify(pasarela, never()).reembolsar(any());
    }

    @Test
    void siElTurnoSeCancelóMientrasElCobroEstabaEnElAireSeDevuelve() {
        turno.setEstado(EstadoTurno.CANCELADO);
        when(pasarela.aprobadoDe("turno-30")).thenReturn(Optional.of(APROBADO));

        assertEquals(Pago.Estado.REEMBOLSADO, intentos.conciliar(1L));

        verify(pasarela).reembolsar("pay_tarde");
        assertNotNull(pendiente.getReembolsadoEn());
        verify(eventoPublisher).publicar(TipoEventoTurno.PAGO_REEMBOLSADO, turno, null);
    }

    @Test
    void siEseCobroYaLoRegistroOtroIntentoEsteQuedaDeMas() {
        when(pasarela.aprobadoDe("turno-30")).thenReturn(Optional.of(APROBADO));
        when(repository.existeConIdExterno("pay_tarde")).thenReturn(true);

        assertEquals(Pago.Estado.FALLIDO, intentos.conciliar(1L));
        verifyNoInteractions(eventoPublisher);
    }

    @Test
    void sinRastroEnLaPasarelaSigueBloqueandoDuranteLaVentanaYDespuesFalla() {
        when(pasarela.aprobadoDe("turno-30")).thenReturn(Optional.empty());
        assertEquals(Pago.Estado.PENDIENTE, intentos.conciliar(1L), "todavia puede aprobarse");

        pendiente.setCreadoEn(LocalDateTime.now().minusSeconds(IntentosPago.VENTANA_POR_DEFECTO + 5));
        assertEquals(Pago.Estado.FALLIDO, intentos.conciliar(1L));
        assertEquals("La pasarela no confirmó el cobro", pendiente.getMotivo());
    }

    @Test
    void laVentanaSeConfigura() {
        System.setProperty("mediconecta.pagos.ventanaSegundos", "5");
        assertEquals(5, IntentosPago.ventanaSegundos());
    }

    @Test
    void soloSeConciliaLoQueEstaPendiente() {
        assertNull(intentos.conciliar(2L));
        pendiente.setEstado(Pago.Estado.APROBADO);
        assertEquals(Pago.Estado.APROBADO, intentos.conciliar(1L));
        verifyNoInteractions(pasarela);
    }

    @Test
    void marcarFallidoSoloCambiaUnPendiente() {
        intentos.marcarFallido(1L, "datos invalidos");
        assertEquals(Pago.Estado.FALLIDO, pendiente.getEstado());
        verify(repository).actualizar(pendiente);

        Pago aprobado = new Pago();
        aprobado.setEstado(Pago.Estado.APROBADO);
        when(repository.buscarPorId(3L)).thenReturn(aprobado);
        intentos.marcarFallido(3L, "x");
        intentos.marcarFallido(4L, "x");
        assertEquals(Pago.Estado.APROBADO, aprobado.getEstado());
    }
}
