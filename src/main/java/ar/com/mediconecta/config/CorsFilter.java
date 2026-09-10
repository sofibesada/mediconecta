package ar.com.mediconecta.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Habilita CORS para que el frontend (abierto como archivo local o servido en
 * otro puerto/origen) pueda consumir la API desde el navegador.
 *
 * - Responde el preflight OPTIONS con las cabeceras Access-Control-*.
 * - Agrega esas cabeceras a todas las respuestas de la API.
 *
 * Se usa "*" como origen permitido porque la API no usa cookies ni sesiones
 * (la autenticacion sensible va por Basic Auth explicito en cada request).
 */
@Provider
@PreMatching
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String ALLOW_ORIGIN = "*";
    private static final String ALLOW_METHODS = "GET, POST, PUT, DELETE, OPTIONS";
    private static final String ALLOW_HEADERS = "origin, content-type, accept, authorization";

    @Override
    public void filter(ContainerRequestContext request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            request.abortWith(Response.ok()
                    .header("Access-Control-Allow-Origin", ALLOW_ORIGIN)
                    .header("Access-Control-Allow-Methods", ALLOW_METHODS)
                    .header("Access-Control-Allow-Headers", ALLOW_HEADERS)
                    .header("Access-Control-Max-Age", "86400")
                    .build());
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        // putSingle (no add) para no duplicar las cabeceras cuando el preflight
        // OPTIONS ya las agrego en abortWith(): un Access-Control-Allow-Origin
        // repetido hace fallar CORS en el navegador.
        response.getHeaders().putSingle("Access-Control-Allow-Origin", ALLOW_ORIGIN);
        response.getHeaders().putSingle("Access-Control-Allow-Methods", ALLOW_METHODS);
        response.getHeaders().putSingle("Access-Control-Allow-Headers", ALLOW_HEADERS);
    }
}
