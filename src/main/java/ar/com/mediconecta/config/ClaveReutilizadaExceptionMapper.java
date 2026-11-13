package ar.com.mediconecta.config;

import ar.com.mediconecta.idempotencia.business.ClaveReutilizadaException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Idempotency-Key reutilizada en otra operacion: 422 Unprocessable Content. */
@Provider
public class ClaveReutilizadaExceptionMapper implements ExceptionMapper<ClaveReutilizadaException> {
    @Override
    public Response toResponse(ClaveReutilizadaException ex) {
        return ApiError.respuesta(Estado.UNPROCESSABLE, ex.getMessage());
    }

    /** JAX-RS no trae una constante para 422. */
    enum Estado implements Response.StatusType {
        UNPROCESSABLE;

        @Override public int getStatusCode() { return 422; }
        @Override public Response.Status.Family getFamily() { return Response.Status.Family.CLIENT_ERROR; }
        @Override public String getReasonPhrase() { return "Unprocessable Content"; }
    }
}
