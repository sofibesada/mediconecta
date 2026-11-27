package ar.com.mediconecta.config;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(GenericExceptionMapper.class.getName());

    static final String CUERPO_INVALIDO = "El cuerpo del pedido no es un JSON válido o tiene un dato del tipo equivocado.";

    /** El JSON del cuerpo no se pudo leer (JSON-B o el parser de JSON-P, en cualquier nivel de la causa). */
    static boolean esCuerpoIlegible(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof jakarta.json.bind.JsonbException || t instanceof jakarta.json.JsonException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Response toResponse(Exception ex) {
        if (ex instanceof WebApplicationException wae) {
            return wae.getResponse();
        }
        // Cuerpo que no es JSON valido o con un dato del tipo equivocado ("abc" donde va un numero):
        // es un error del cliente (400), no del servidor (D-15, lo encontro OWASP ZAP).
        if (esCuerpoIlegible(ex)) {
            LOG.info("[API] cuerpo de pedido ilegible: " + ex.getMessage());
            return ApiError.respuesta(Response.Status.BAD_REQUEST, CUERPO_INVALIDO);
        }
        // Error inesperado: el detalle va SOLO al log del servidor, nunca a la respuesta.
        LOG.log(java.util.logging.Level.SEVERE, "[API] error inesperado", ex);
        return ApiError.respuesta(Response.Status.INTERNAL_SERVER_ERROR, ApiError.ERROR_INESPERADO);
    }
}
