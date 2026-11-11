package ar.com.mediconecta.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ar.com.mediconecta.obrassociales.integracion.ObrasSocialesNoDisponibleException;
import ar.com.mediconecta.telemedicina.business.SalaNoDisponibleException;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.EJBException;
import jakarta.jms.JMSException;
import jakarta.jms.JMSRuntimeException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Cada falla del dominio o de la infraestructura se traduce a un codigo HTTP y un JSON {error}. */
class ExceptionMappersTest {

    private final EJBExceptionMapper ejb = new EJBExceptionMapper();

    @AfterEach
    void limpiar() {
        System.clearProperty("una.propiedad.de.prueba");
    }

    @Test
    void sinPermisoDeRolEs403() {
        assertRespuesta(403, "No tenés permiso para hacer esta operación", ejb.toResponse(new EJBAccessException("x")));
    }

    @Test
    void obraSocialCaidaEs503() {
        EJBException e = new EJBException(new ObrasSocialesNoDisponibleException("El legado no responde", null));
        assertRespuesta(503, "El legado no responde", ejb.toResponse(e));
    }

    @Test
    void brokerCaidoEs503AunqueVengaAnidado() {
        EJBException e = new EJBException(new RuntimeException("envio", new JMSRuntimeException("sin broker")));
        Response r = ejb.toResponse(e);
        assertEquals(503, r.getStatus());

        EJBException e2 = new EJBException(new IllegalStateException("x", new JMSException("sin broker")));
        assertEquals(503, ejb.toResponse(e2).getStatus());
    }

    @Test
    void datosInvalidosEs400YConflictoEs409() {
        assertRespuesta(400, "dato malo", ejb.toResponse(new EJBException(new IllegalArgumentException("dato malo"))));
        assertRespuesta(409, "ocupado", ejb.toResponse(new EJBException(new IllegalStateException("ocupado"))));
    }

    @Test
    void otraFallaEs500SinMensajeInterno() {
        assertRespuesta(500, "Error interno", ejb.toResponse(new EJBException(new RuntimeException())));
        assertRespuesta(500, "solo", ejb.toResponse(new EJBException("solo")));
    }

    @Test
    void mappersDeExcepcionesDelDominio() {
        assertRespuesta(400, "malo", new IllegalArgumentExceptionMapper().toResponse(new IllegalArgumentException("malo")));
        assertRespuesta(409, "choca", new IllegalStateExceptionMapper().toResponse(new IllegalStateException("choca")));
        assertRespuesta(404, "sin sala", new SalaNoDisponibleExceptionMapper().toResponse(new SalaNoDisponibleException("sin sala")));
    }

    @Test
    void elGenericoRespetaLasRespuestasDeJaxRs() {
        GenericExceptionMapper g = new GenericExceptionMapper();
        assertEquals(404, g.toResponse(new NotFoundException()).getStatus());
        assertRespuesta(500, "boom", g.toResponse(new RuntimeException("boom")));
        assertRespuesta(500, "Error interno", g.toResponse(new RuntimeException()));
    }

    @Test
    void configLeePropiedadAntesQueVariableDeEntorno() {
        assertNull(Config.valor("una.propiedad.de.prueba", "VARIABLE_QUE_NO_EXISTE_EN_EL_ENTORNO"));
        System.setProperty("una.propiedad.de.prueba", "  ");
        assertNull(Config.valor("una.propiedad.de.prueba", "VARIABLE_QUE_NO_EXISTE_EN_EL_ENTORNO"));
        System.setProperty("una.propiedad.de.prueba", "valor");
        assertEquals("valor", Config.valor("una.propiedad.de.prueba", "PATH"));
        System.clearProperty("una.propiedad.de.prueba");
        assertEquals(System.getenv("PATH"), Config.valor("una.propiedad.de.prueba", "PATH"));
    }

    private static void assertRespuesta(int estado, String mensaje, Response r) {
        assertEquals(estado, r.getStatus());
        assertEquals(mensaje, ((ApiError) r.getEntity()).error);
    }
}
