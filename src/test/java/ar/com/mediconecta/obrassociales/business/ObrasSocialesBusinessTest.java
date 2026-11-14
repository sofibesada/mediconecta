package ar.com.mediconecta.obrassociales.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.obrassociales.business.IObraSocialService.CoberturaMedicamento;
import ar.com.mediconecta.obrassociales.business.IObraSocialService.CoberturaVerificada;
import ar.com.mediconecta.obrassociales.business.IObraSocialService.ObraSocialDisponible;
import ar.com.mediconecta.obrassociales.business.IObraSocialService.ResultadoAutorizacion;
import ar.com.mediconecta.obrassociales.business.IObraSocialService.ResultadoReclamo;
import ar.com.mediconecta.obrassociales.integracion.ContratoLegado;
import ar.com.mediconecta.obrassociales.integracion.ObrasSocialesClient;
import ar.com.mediconecta.obrassociales.integracion.ObrasSocialesNoDisponibleException;
import ar.com.mediconecta.obrassociales.integracion.ObrasSocialesPortType;
import ar.com.mediconecta.obrassociales.model.IntentoAutorizacion;
import ar.com.mediconecta.obrassociales.model.IntentoAutorizacion.Resultado;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Adapter del legado SOAP y auditoria de autorizaciones. */
@ExtendWith(MockitoExtension.class)
class ObrasSocialesBusinessTest {

    @Mock private ObrasSocialesClient cliente;
    @Mock private ObrasSocialesPortType puerto;
    @Mock private EntityManager em;

    @InjectMocks private ObraSocialService adapter;
    @InjectMocks private AuditoriaAutorizacionService auditoria;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void elClienteLlamaAlPuerto() {
        lenient().when(cliente.llamar(anyString(), any())).thenAnswer(i -> ((Function<ObrasSocialesPortType, Object>) i.getArgument(1)).apply(puerto));
    }

    @Test
    void traduceElListadoDeObrasSociales() {
        ContratoLegado.ObraSocial os = new ContratoLegado.ObraSocial();
        os.codigo = "OSDE";
        os.nombre = "OS Medica";
        os.tipo = "PREPAGA";
        when(puerto.listarObrasSociales()).thenReturn(List.of(os));

        assertEquals(List.of(new ObraSocialDisponible("OSDE", "OS Medica", "PREPAGA")), adapter.listarObrasSociales());
    }

    @Test
    void traduceLaCobertura() {
        ContratoLegado.ResultadoCobertura r = new ContratoLegado.ResultadoCobertura();
        r.cubierto = true;
        r.plan = "Plan 210";
        r.copagoPorcentaje = 30;
        when(puerto.consultarCobertura("OSDE", "12345678")).thenReturn(r);

        assertEquals(new CoberturaVerificada(true, "Plan 210", 30, null), adapter.verificarCobertura("OSDE", "12345678"));
    }

    @Test
    void autorizaConElIdDelTurnoComoReferencia() {
        ContratoLegado.ResultadoAutorizacion r = new ContratoLegado.ResultadoAutorizacion();
        r.autorizada = true;
        r.codigoAutorizacion = "AUT-1";
        LocalDateTime fecha = LocalDateTime.of(2026, 12, 1, 10, 0);
        when(puerto.autorizarPrestacion("OSDE", "12345678", IObraSocialService.PRESTACION_CONSULTA, fecha.toString(), "turno-100")).thenReturn(r);

        assertEquals(new ResultadoAutorizacion(true, "AUT-1", null), adapter.autorizarConsulta("OSDE", "12345678", 100L, fecha));
    }

    @Test
    void coberturaDeMedicamentos() {
        assertTrue(adapter.coberturaMedicamentos("OSDE", "1", List.of()).isEmpty());
        verifyNoInteractions(cliente);

        ContratoLegado.CoberturaMedicamento c = new ContratoLegado.CoberturaMedicamento();
        c.medicamento = "Enalapril";
        c.cubierto = true;
        c.descuentoPorcentaje = 40;
        when(puerto.consultarCoberturaMedicamentos("OSDE", "1", List.of("Enalapril"))).thenReturn(List.of(c));

        assertEquals(List.of(new CoberturaMedicamento("Enalapril", true, 40)),
                adapter.coberturaMedicamentos("OSDE", "1", List.of("Enalapril")));
    }

    @Test
    void reclamoConReferenciaIdempotente() {
        ContratoLegado.ResultadoReclamo r = new ContratoLegado.ResultadoReclamo();
        r.aceptado = true;
        r.numeroReclamo = "REC-1";
        r.importeReconocido = BigDecimal.TEN;
        when(puerto.registrarReclamo("OSDE", "1", "AUT-1", BigDecimal.TEN, "reclamo-turno-100")).thenReturn(r);

        assertEquals(new ResultadoReclamo(true, "REC-1", BigDecimal.TEN, null),
                adapter.registrarReclamo("OSDE", "1", "AUT-1", BigDecimal.TEN, 100L));
    }

    @Test
    void siElLegadoNoRespondeSePropaga() {
        org.mockito.Mockito.doThrow(new ObrasSocialesNoDisponibleException("caido", null)).when(cliente).llamar(anyString(), any());
        assertThrows(ObrasSocialesNoDisponibleException.class, () -> adapter.verificarCobertura("OSDE", "1"));
    }

    @Test
    void auditaCadaIntento() {
        auditoria.registrar(100L, 1L, "OSDE", Resultado.RECHAZADA, null, "Tope alcanzado");

        ArgumentCaptor<IntentoAutorizacion> captor = ArgumentCaptor.forClass(IntentoAutorizacion.class);
        verify(em).persist(captor.capture());
        IntentoAutorizacion i = captor.getValue();
        assertEquals(Resultado.RECHAZADA, i.getResultado());
        assertEquals("Tope alcanzado", i.getMotivo());
        assertFalse(i.getFecha() == null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listaLosUltimosIntentos() {
        TypedQuery<IntentoAutorizacion> q = org.mockito.Mockito.mock(TypedQuery.class);
        List<IntentoAutorizacion> lista = List.of(new IntentoAutorizacion());
        when(em.createQuery(anyString(), org.mockito.ArgumentMatchers.eq(IntentoAutorizacion.class))).thenReturn(q);
        when(q.setMaxResults(50)).thenReturn(q);
        when(q.getResultList()).thenReturn(lista);

        assertSame(lista, auditoria.listarRecientes());
    }
}
