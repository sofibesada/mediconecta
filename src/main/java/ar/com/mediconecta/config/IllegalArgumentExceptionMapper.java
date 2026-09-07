package ar.com.mediconecta.config;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce los datos de entrada invalidos del negocio (p. ej. paciente
 * inexistente) a HTTP 400 Bad Request con cuerpo JSON.
 */
@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

    @Override
    public Response toResponse(IllegalArgumentException ex) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(ex.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
