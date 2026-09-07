package ar.com.mediconecta.config;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce los conflictos de estado del negocio (turno no disponible, hold
 * expirado, cancelacion fuera de plazo) a HTTP 409 Conflict con cuerpo JSON.
 */
@Provider
public class IllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {

    @Override
    public Response toResponse(IllegalStateException ex) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(ex.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
