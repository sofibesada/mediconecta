package ar.com.mediconecta.config;

import ar.com.mediconecta.obrassociales.integracion.ObrasSocialesNoDisponibleException;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.EJBException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class EJBExceptionMapper implements ExceptionMapper<EJBException> {

    @Override
    public Response toResponse(EJBException ex) {
        if (ex instanceof EJBAccessException) {
            // Lo lanza el contenedor cuando el rol del usuario no esta en el @RolesAllowed del EJB.
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ApiError("No tenés permiso para hacer esta operación"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        Throwable causa = ex.getCause() != null ? ex.getCause() : ex;

        Response.Status estado;
        if (causa instanceof ObrasSocialesNoDisponibleException) {
            estado = Response.Status.SERVICE_UNAVAILABLE;
        } else if (esFallaDeMensajeria(ex)) {
            // Broker caido: la transaccion ya se revirtio (no quedo nada a medias).
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(new ApiError("El servicio de mensajería no está disponible. No se guardó ningún cambio; probá de nuevo en unos minutos."))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } else if (causa instanceof IllegalArgumentException) {
            estado = Response.Status.BAD_REQUEST;
        } else if (causa instanceof IllegalStateException) {
            estado = Response.Status.CONFLICT;
        } else {
            estado = Response.Status.INTERNAL_SERVER_ERROR;
        }

        String mensaje = causa.getMessage() != null ? causa.getMessage() : "Error interno";
        return Response.status(estado)
                .entity(new ApiError(mensaje))
                .type(MediaType.APPLICATION_JSON)
                .build();
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
