package ar.com.mediconecta.facturacion.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.facturacion.data.ReclamoRepository;
import ar.com.mediconecta.facturacion.messaging.FacturacionColaPublisher;
import ar.com.mediconecta.facturacion.model.Reclamo;
import ar.com.mediconecta.obrassociales.business.IObraSocialService;
import ar.com.mediconecta.obrassociales.business.IObraSocialService.ResultadoReclamo;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FacturacionServiceTest {

    @Mock private ReclamoRepository reclamoRepository;
    @Mock private FacturacionColaPublisher colaPublisher;
    @Mock private IObraSocialService obraSocialService;

    @InjectMocks private FacturacionService service;

    @Test
    void registrarDejaElReclamoPendienteYLoEncola() {
        service.registrarPrestacion(100L, 1L, "OSMED", "12345678", "AUT-1", new BigDecimal("14000"));

        ArgumentCaptor<Reclamo> captor = ArgumentCaptor.forClass(Reclamo.class);
        verify(reclamoRepository).guardar(captor.capture());
        Reclamo r = captor.getValue();
        assertEquals(Reclamo.Estado.PENDIENTE, r.getEstado());
        assertEquals("AUT-1", r.getCodigoAutorizacion());
        assertEquals(new BigDecimal("14000"), r.getImporte());
        assertNotNull(r.getCreadoEn());
        verify(colaPublisher).enviar(r.getId(), 100L);
    }

    @Test
    void procesarUnReclamoAceptado() {
        Reclamo r = pendiente();
        when(reclamoRepository.buscarPorId(5L)).thenReturn(r);
        when(obraSocialService.registrarReclamo("OSMED", "12345678", "AUT-1", new BigDecimal("14000"), 100L))
                .thenReturn(new ResultadoReclamo(true, "REC-9", new BigDecimal("14000"), null));

        service.procesarReclamo(5L);

        assertEquals(Reclamo.Estado.ENVIADO, r.getEstado());
        assertEquals("REC-9", r.getNumeroReclamo());
        assertNotNull(r.getProcesadoEn());
    }

    @Test
    void procesarUnReclamoRechazado() {
        Reclamo r = pendiente();
        when(reclamoRepository.buscarPorId(5L)).thenReturn(r);
        when(obraSocialService.registrarReclamo(any(), any(), any(), any(), anyLong()))
                .thenReturn(new ResultadoReclamo(false, null, null, "Autorizacion inexistente"));

        service.procesarReclamo(5L);

        assertEquals(Reclamo.Estado.RECHAZADO, r.getEstado());
        assertEquals("Autorizacion inexistente", r.getMotivo());
    }

    @Test
    void unMensajeRepetidoNoSeVuelveAFacturar() {
        Reclamo enviado = pendiente();
        enviado.setEstado(Reclamo.Estado.ENVIADO);
        when(reclamoRepository.buscarPorId(5L)).thenReturn(enviado);
        when(reclamoRepository.buscarPorId(6L)).thenReturn(null);

        service.procesarReclamo(5L);
        service.procesarReclamo(6L);

        verifyNoInteractions(obraSocialService);
    }

    @Test
    void listarRecientes() {
        when(reclamoRepository.listarRecientes(50)).thenReturn(List.of(pendiente()));
        assertEquals(1, service.listarRecientes().size());
    }

    private static Reclamo pendiente() {
        Reclamo r = new Reclamo();
        r.setTurnoId(100L);
        r.setObraSocial("OSMED");
        r.setNumeroAfiliado("12345678");
        r.setCodigoAutorizacion("AUT-1");
        r.setImporte(new BigDecimal("14000"));
        r.setEstado(Reclamo.Estado.PENDIENTE);
        return r;
    }
}
