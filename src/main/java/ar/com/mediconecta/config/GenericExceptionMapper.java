package ar.com.mediconecta.config;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(GenericExceptionMapper.class.getName());

    @Override
    public Response toResponse(Exception ex) {
        if (ex instanceof WebApplicationException wae) {
            return wae.getResponse();
        }
        // Error inesperado: el detalle va SOLO al log del servidor, nunca a la respuesta.
        LOG.log(java.util.logging.Level.SEVERE, "[API] error inesperado", ex);
        return ApiError.respuesta(Response.Status.INTERNAL_SERVER_ERROR, ApiError.ERROR_INESPERADO);
    }
}
