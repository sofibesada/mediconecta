package ar.com.mediconecta.pagos.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.util.Optional;
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
    private static final BigDecimal IMPORTE = new BigDecimal("6000.00");
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
        turno.setImporteCopago(IMPORTE);
        lenient().when(turnoService.buscarTurnoParaPagar(TURNO)).thenReturn(turno);
        lenient().when(repository.aprobadosDe(List.of(TURNO))).thenReturn(List.of());
        lenient().when(repository.listarDeTurno(TURNO)).thenReturn(List.of());
        lenient().when(pasarela.aprobadoDe("turno-30")).thenReturn(Optional.empty());
    }

    private static PasarelaPagosClient.Resultado resultado(String estado, String motivo) {
        return new PasarelaPagosClient.Resultado("pay_1", estado, motivo, "Visa", "1111", "APROBADO".equals(estado) ? "123456" : null);
    }

    @Test
    void unPagoAprobadoBloqueaElTurnoYUsaUnaClavePropiaHaciaLaPasarela() {
        String clave = PagoService.claveParaPasarela(TURNO, 1, "4111111111111111", "12/30", IMPORTE);
        when(pasarela.cobrar(eq(IMPORTE), eq("turno-30"), eq("4111111111111111"), eq("12/30"), eq("123"), eq("ANA PEREZ"), eq(clave)))
                .thenReturn(resultado("APROBADO", null));

        Pago p = service.pagar(PACIENTE, TURNO, VISA);

        assertEquals(Pago.Estado.APROBADO, p.getEstado());
        assertEquals("1111", p.getUltimos4());
        assertEquals("123456", p.getCodigoAutorizacion());
        assertEquals(IMPORTE, p.getImporte());
        verify(turnoService).buscarTurnoParaPagar(TURNO);
        verify(repository).guardar(p);
        verify(eventoPublisher).publicar(TipoEventoTurno.PAGO_ACREDITADO, turno, null);
    }

    @Test
    void siLaPasarelaYaAproboEsteTurnoSeConciliaSinCobrarDeNuevo() {
        // Caso: la respuesta anterior se perdio (timeout o caida del broker) pero el cobro existe.
        when(pasarela.aprobadoDe("turno-30")).thenReturn(Optional.of(
                new PasarelaPagosClient.Resultado("pay_viejo", "APROBADO", null, "Mastercard", "4444", "654321")));

        Pago p = service.pagar(PACIENTE, TURNO, VISA);

        assertEquals("pay_viejo", p.getIdExterno());
        assertEquals("4444", p.getUltimos4(), "se registra la tarjeta que realmente se cobro");
        verify(pasarela, never()).cobrar(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(eventoPublisher).publicar(TipoEventoTurno.PAGO_ACREDITADO, turno, null);
    }

    @Test
    void laClaveDistingueTurnoIntentoYTarjetaYNoLlevaElNumero() {
        String base = PagoService.claveParaPasarela(30L, 1, "4111111111111111", "12/30", IMPORTE);
        assertEquals(base, PagoService.claveParaPasarela(30L, 1, "4111111111111111", "12/30", new BigDecimal("6000")),
                "el mismo intento con la misma tarjeta repite la clave (no cobra dos veces)");
        assertNotEquals(base, PagoService.claveParaPasarela(31L, 1, "4111111111111111", "12/30", IMPORTE));
        assertNotEquals(base, PagoService.claveParaPasarela(30L, 2, "4111111111111111", "12/30", IMPORTE));
        assertNotEquals(base, PagoService.claveParaPasarela(30L, 1, "5555555555554444", "12/30", IMPORTE));
        assertTrue(base.startsWith("mc-turno-30-1-"));
        assertTrue(!base.contains("4111111111111111"), "la clave no lleva la tarjeta en claro");
    }

    @Test
    void despuesDeUnRechazoElSiguienteIntentoUsaOtraClave() {
        Pago rechazado = new Pago();
        when(repository.listarDeTurno(TURNO)).thenReturn(List.of(rechazado));
        when(pasarela.cobrar(any(), anyString(), anyString(), anyString(), anyString(), anyString(), startsWith("mc-turno-30-2-")))
                .thenReturn(resultado("APROBADO", null));

        assertEquals(Pago.Estado.APROBADO, service.pagar(PACIENTE, TURNO, VISA).getEstado());
    }

    @Test
    void unRechazoQuedaRegistradoYNoAvisa() {
        when(pasarela.cobrar(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(resultado("RECHAZADO", "Fondos insuficientes"));

        PagoRechazadoException e = assertThrows(PagoRechazadoException.class, () -> service.pagar(PACIENTE, TURNO, VISA));

        assertEquals("El pago fue rechazado: Fondos insuficientes", e.getMessage());
        ArgumentCaptor<Pago> guardado = ArgumentCaptor.forClass(Pago.class);
        verify(repository).guardar(guardado.capture());
        assertEquals(Pago.Estado.RECHAZADO, guardado.getValue().getEstado());
        verifyNoInteractions(eventoPublisher);
    }

    @Test
    void elRechazoSinMotivoSugiereConsultarAlBanco() {
        assertEquals("El pago fue rechazado: consultá con tu banco", new PagoRechazadoException(null).getMessage());
    }

    @Test
    void soloElPacienteDelTurnoPuedePagarlo() {
        assertThrows(IllegalArgumentException.class, () -> service.pagar(99L, TURNO, VISA));
        when(turnoService.buscarTurnoParaPagar(1L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.pagar(PACIENTE, 1L, VISA));
        verifyNoInteractions(pasarela);
    }

    @Test
    void soloSePaganTurnosConfirmadosConImporteYSinPagar() {
        turno.setEstado(EstadoTurno.RESERVADO_TEMPORAL);
        assertEquals("Solo se pueden pagar turnos confirmados",
                assertThrows(IllegalStateException.class, () -> service.pagar(PACIENTE, TURNO, VISA)).getMessage());

        turno.setEstado(EstadoTurno.CONFIRMADO);
        turno.setImporteCopago(null);
        assertThrows(IllegalStateException.class, () -> service.pagar(PACIENTE, TURNO, VISA));
        turno.setImporteCopago(BigDecimal.ZERO);
        assertEquals("Este turno no tiene un importe a pagar",
                assertThrows(IllegalStateException.class, () -> service.pagar(PACIENTE, TURNO, VISA)).getMessage());

        turno.setImporteCopago(IMPORTE);
        when(repository.aprobadosDe(List.of(TURNO))).thenReturn(List.of(new Pago()));
        assertEquals("Este turno ya está pagado",
                assertThrows(IllegalStateException.class, () -> service.pagar(PACIENTE, TURNO, VISA)).getMessage());
        verifyNoInteractions(pasarela);
    }

    @Test
    void unaTarjetaInvalidaNoLlegaALaPasarela() {
        assertThrows(IllegalArgumentException.class, () -> service.pagar(PACIENTE, TURNO, null));
        PagoService.DatosTarjeta mala = new PagoService.DatosTarjeta("4111 1111 1111 1112", "12/30", "123", "ANA");
        assertThrows(IllegalArgumentException.class, () -> service.pagar(PACIENTE, TURNO, mala));
        verifyNoInteractions(pasarela);
        verify(repository, never()).guardar(any());
    }

    @Test
    void alCancelarUnTurnoPagadoSeDevuelveYSeAvisa() {
        Pago aprobado = new Pago();
        aprobado.setTurnoId(TURNO);
        aprobado.setEstado(Pago.Estado.APROBADO);
        aprobado.setIdExterno("pay_1");
        when(repository.aprobadosDe(List.of(TURNO))).thenReturn(List.of(aprobado));
        when(turnoService.buscarTurno(TURNO)).thenReturn(turno);

        assertEquals(1, service.reembolsarDeTurno(TURNO));

        verify(pasarela).reembolsar("pay_1");
        assertEquals(Pago.Estado.REEMBOLSADO, aprobado.getEstado());
        assertNotNull(aprobado.getReembolsadoEn());
        verify(repository).actualizar(aprobado);
        verify(eventoPublisher).publicar(TipoEventoTurno.PAGO_REEMBOLSADO, turno, null);
    }

    @Test
    void cancelarUnTurnoSinPagosNoHaceNada() {
        assertEquals(0, service.reembolsarDeTurno(TURNO));
        verifyNoInteractions(pasarela, eventoPublisher);
    }

    @Test
    void consultas() {
        Pago a = new Pago();
        a.setTurnoId(1L);
        Pago b = new Pago();
        b.setTurnoId(2L);
        when(repository.vigentesDe(List.of(1L, 2L))).thenReturn(List.of(a, b));
        when(repository.listarDeTurno(TURNO)).thenReturn(List.of(a));
        when(repository.listarRecientes(50)).thenReturn(List.of(b));

        Map<Long, Pago> porTurno = service.vigentesDe(List.of(1L, 2L));
        assertSame(a, porTurno.get(1L));
        assertSame(b, porTurno.get(2L));
        assertEquals(List.of(a), service.deTurno(TURNO));
        assertTrue(service.listarRecientes().contains(b));
        verify(repository, times(1)).listarRecientes(50);
    }
}
