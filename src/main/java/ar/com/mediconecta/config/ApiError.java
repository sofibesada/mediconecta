package ar.com.mediconecta.config;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Cuerpo de los errores de la API en formato Problem Details (RFC 9457):
 *
 * <pre>
 * {"type": "about:blank", "title": "Conflict", "status": 409,
 *  "detail": "No se puede cancelar: faltan menos de 24hs para el turno",
 *  "error": "No se puede cancelar: faltan menos de 24hs para el turno"}
 * </pre>
 *
 * "error" es un miembro de extension con el mismo texto que "detail": lo leen los clientes
 * que ya existian (frontend, pruebas, colecciones de Postman), asi el cambio de formato no
 * rompe a nadie. Nunca lleva datos internos (stack traces, SQL, ids de otros usuarios).
 */
public class ApiError {

    public static final String PROBLEM_JSON = "application/problem+json";
    public static final MediaType PROBLEM_JSON_TYPE = MediaType.valueOf(PROBLEM_JSON);

    /** Sin un catalogo propio de tipos de error, el RFC indica "about:blank". */
    public String type = "about:blank";
    /** Texto corto del codigo HTTP (por ejemplo, "Conflict"). */
    public String title;
    public int status;
    /** Explicacion para el usuario. */
    public String detail;
    /** Igual a detail; se mantiene por compatibilidad con los clientes anteriores. */
    public String error;

    public ApiError() {}

    public ApiError(String mensaje) {
        this.detail = mensaje;
        this.error = mensaje;
    }

    public ApiError(Response.StatusType estado, String mensaje) {
        this(mensaje);
        this.status = estado.getStatusCode();
        this.title = estado.getReasonPhrase();
    }

    /** Respuesta de error con el cuerpo Problem Details y el Content-Type del RFC. */
    public static Response respuesta(Response.StatusType estado, String mensaje) {
        return Response.status(estado).entity(new ApiError(estado, mensaje)).type(PROBLEM_JSON_TYPE).build();
    }
}
