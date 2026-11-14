package ar.com.mediconecta.config;

import ar.com.mediconecta.pagos.business.PagoRechazadoException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** La pasarela rechazo el cobro: 402 Payment Required con el motivo. */
@Provider
public class PagoRechazadoExceptionMapper implements ExceptionMapper<PagoRechazadoException> {
    @Override
    public Response toResponse(PagoRechazadoException ex) {
        return ApiError.respuesta(Response.Status.PAYMENT_REQUIRED, ex.getMessage());
    }
}
