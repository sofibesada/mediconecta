package ar.com.mediconecta.config;

import ar.com.mediconecta.seguridad.IngresoBloqueadoException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Demasiados intentos fallidos de ingreso: 429 con Retry-After (segundos) y Problem Details. */
@Provider
public class IngresoBloqueadoExceptionMapper implements ExceptionMapper<IngresoBloqueadoException> {

    @Override
    public Response toResponse(IngresoBloqueadoException ex) {
        Response r = ApiError.respuesta(Response.Status.TOO_MANY_REQUESTS, ex.getMessage());
        return Response.fromResponse(r).header("Retry-After", ex.getSegundosParaReintentar()).build();
    }
}
