package ar.com.mediconecta.config;

import ar.com.mediconecta.telemedicina.business.SalaNoDisponibleException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Turno sin sala, inexistente o de otra persona: 404 con el mismo cuerpo en los tres
 * casos, para no confirmar si el turno existe.
 */
@Provider
public class SalaNoDisponibleExceptionMapper implements ExceptionMapper<SalaNoDisponibleException> {

    @Override
    public Response toResponse(SalaNoDisponibleException ex) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(ex.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
