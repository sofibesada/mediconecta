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
import java.util.function.BiConsumer;
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
        aplicar(request.getHeaderString("Origin"), h::putSingle);
    }

    /**
     * Pone los encabezados CORS con el setter recibido. Lo usa tambien el mecanismo de
     * autenticacion: un 401 que corta el pedido antes de JAX-RS tambien tiene que llevar
     * CORS, si no el navegador lo ve como "error de red" y el frontend no se entera.
     */
    public static void aplicar(String origen, BiConsumer<String, String> setter) {
        if (origen != null && origenesPermitidos().contains(origen)) {
            setter.accept("Access-Control-Allow-Origin", origen);
            setter.accept("Access-Control-Allow-Credentials", "true");
            setter.accept("Vary", "Origin");
        } else {
            setter.accept("Access-Control-Allow-Origin", "*");
        }
        setter.accept("Access-Control-Allow-Methods", ALLOW_METHODS);
        setter.accept("Access-Control-Allow-Headers", ALLOW_HEADERS);
    }

    private static Set<String> origenesPermitidos() {
        return Arrays.stream(System.getProperty("mediconecta.cors.origenes", ORIGENES_POR_DEFECTO).split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
    }
}
