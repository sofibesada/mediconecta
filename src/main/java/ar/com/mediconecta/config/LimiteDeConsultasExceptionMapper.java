package ar.com.mediconecta.config;

import ar.com.mediconecta.ia.business.LimiteDeConsultasException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Demasiadas consultas al orientador: 429 con Retry-After (segundos) y Problem Details. */
@Provider
public class LimiteDeConsultasExceptionMapper implements ExceptionMapper<LimiteDeConsultasException> {

    @Override
    public Response toResponse(LimiteDeConsultasException ex) {
        Response r = ApiError.respuesta(Response.Status.TOO_MANY_REQUESTS, ex.getMessage());
        return Response.fromResponse(r).header("Retry-After", ex.getSegundosParaReintentar()).build();
    }
}
