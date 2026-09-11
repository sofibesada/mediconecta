package ar.com.mediconecta.config;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Red de seguridad: cualquier excepcion que ningun mapper especifico
 * (IllegalArgumentException, IllegalStateException, EJBException) resuelva
 * cae aca en vez de filtrarse como una pagina de error no-JSON. Los
 * WebApplicationException (NotFoundException, etc.) conservan su propia
 * respuesta.
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception ex) {
        if (ex instanceof WebApplicationException wae) {
            return wae.getResponse();
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(ex.getMessage() != null ? ex.getMessage() : "Error interno"))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
