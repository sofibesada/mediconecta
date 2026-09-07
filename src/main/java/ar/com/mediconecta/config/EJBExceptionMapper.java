package ar.com.mediconecta.config;

import jakarta.ejb.EJBException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Los EJB (@Stateless / @Stateful) envuelven las excepciones unchecked del
 * negocio en EJBException. Este mapper la desenvuelve y reusa el criterio de
 * los mappers concretos: IllegalArgumentException -> 400,
 * IllegalStateException -> 409, resto -> 500.
 */
@Provider
public class EJBExceptionMapper implements ExceptionMapper<EJBException> {

    @Override
    public Response toResponse(EJBException ex) {
        Throwable causa = ex.getCause() != null ? ex.getCause() : ex;

        Response.Status estado;
        if (causa instanceof IllegalArgumentException) {
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
}
