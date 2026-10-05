package ar.com.mediconecta.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CORS para el frontend, que se sirve desde otro origen (puerto 5173).
 *
 * A los origenes conocidos se les permite enviar cookies (Allow-Credentials): hace
 * falta para la cookie de sesion que ata al paciente con su AsistenteAgendamiento
 * (@Stateful). Con credenciales el navegador NO acepta "*", por eso se devuelve el
 * origen exacto. Cualquier otro origen sigue pudiendo usar la API, pero sin cookies.
 *
 * Origenes permitidos: system property mediconecta.cors.origenes (separados por coma).
 */
@Provider
@PreMatching
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String ORIGENES_POR_DEFECTO = "http://localhost:5173,http://127.0.0.1:5173";
    private static final String ALLOW_METHODS = "GET, POST, PUT, DELETE, OPTIONS";
    private static final String ALLOW_HEADERS = "origin, content-type, accept, authorization";

    @Override
    public void filter(ContainerRequestContext request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            Response.ResponseBuilder preflight = Response.ok().header("Access-Control-Max-Age", "86400");
            request.abortWith(preflight.build());
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        // putSingle (no add): un Access-Control-Allow-Origin repetido hace fallar CORS.
        MultivaluedMap<String, Object> h = response.getHeaders();
        String origen = request.getHeaderString("Origin");
        if (origen != null && origenesPermitidos().contains(origen)) {
            h.putSingle("Access-Control-Allow-Origin", origen);
            h.putSingle("Access-Control-Allow-Credentials", "true");
            h.putSingle("Vary", "Origin");
        } else {
            h.putSingle("Access-Control-Allow-Origin", "*");
        }
        h.putSingle("Access-Control-Allow-Methods", ALLOW_METHODS);
        h.putSingle("Access-Control-Allow-Headers", ALLOW_HEADERS);
    }

    private Set<String> origenesPermitidos() {
        return Arrays.stream(System.getProperty("mediconecta.cors.origenes", ORIGENES_POR_DEFECTO).split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
    }
}
