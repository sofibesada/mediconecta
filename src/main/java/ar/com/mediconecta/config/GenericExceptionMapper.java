package ar.com.mediconecta.config;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception ex) {
        if (ex instanceof WebApplicationException wae) {
            return wae.getResponse();
        }
        return ApiError.respuesta(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage() != null ? ex.getMessage() : "Error interno");
    }
}
