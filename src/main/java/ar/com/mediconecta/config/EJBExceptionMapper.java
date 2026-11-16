package ar.com.mediconecta.config;

import java.util.logging.Level;
import java.util.logging.Logger;
import ar.com.mediconecta.obrassociales.integracion.ObrasSocialesNoDisponibleException;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.EJBException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class EJBExceptionMapper implements ExceptionMapper<EJBException> {

    private static final Logger LOG = Logger.getLogger(EJBExceptionMapper.class.getName());

    @Override
    public Response toResponse(EJBException ex) {
        if (ex instanceof EJBAccessException) {
            // Lo lanza el contenedor cuando el rol del usuario no esta en el @RolesAllowed del EJB.
            return ApiError.respuesta(Response.Status.FORBIDDEN, "No tenés permiso para hacer esta operación");
        }
        // La causa real puede venir envuelta en mas de un EJBException (un EJB que llama a
        // otro dentro de la misma transaccion): se busca la primera que no sea del contenedor.
        Throwable causa = ex;
        while (causa instanceof EJBException && causa.getCause() != null) {
            causa = causa.getCause();
        }
        if (esConcurrencia(causa)) {
            return ApiError.respuesta(Response.Status.CONFLICT,
                    "Otra persona modificó este turno al mismo tiempo. Actualizá la pantalla y probá de nuevo.");
        }

        Response.Status estado;
        if (causa instanceof ObrasSocialesNoDisponibleException
                || causa instanceof ar.com.mediconecta.pagos.integracion.PasarelaNoDisponibleException) {
            estado = Response.Status.SERVICE_UNAVAILABLE;
        } else if (esFallaDeMensajeria(ex)) {
            // Broker caido: la transaccion ya se revirtio (no quedo nada a medias).
            return ApiError.respuesta(Response.Status.SERVICE_UNAVAILABLE, "El servicio de mensajería no está disponible. No se guardó ningún cambio; probá de nuevo en unos minutos.");
        } else if (causa instanceof IllegalArgumentException) {
            estado = Response.Status.BAD_REQUEST;
        } else if (causa instanceof IllegalStateException) {
            estado = Response.Status.CONFLICT;
        } else {
            estado = Response.Status.INTERNAL_SERVER_ERROR;
        }

        if (estado == Response.Status.INTERNAL_SERVER_ERROR) {
            // Error inesperado: el detalle (SQL, clases, datos) va SOLO al log del servidor.
            LOG.log(Level.SEVERE, "[API] error inesperado", causa);
            return ApiError.respuesta(estado, ApiError.ERROR_INESPERADO);
        }
        String mensaje = causa.getMessage() != null ? causa.getMessage() : ApiError.ERROR_INESPERADO;
        return ApiError.respuesta(estado, mensaje);
    }

    /** Dos transacciones modificaron el mismo registro (@Version de JPA). */
    private static boolean esConcurrencia(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof jakarta.persistence.OptimisticLockException) {
                return true;
            }
        }
        return false;
    }

    private static boolean esFallaDeMensajeria(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof jakarta.jms.JMSRuntimeException || c instanceof jakarta.jms.JMSException) {
                return true;
            }
        }
        return false;
    }
}
