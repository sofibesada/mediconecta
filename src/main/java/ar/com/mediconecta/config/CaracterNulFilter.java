package ar.com.mediconecta.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Rechaza con 400 los pedidos que traen el caracter NUL (U+0000) en la URL o en el cuerpo.
 *
 * PostgreSQL no lo acepta en columnas de texto: un email o un nombre con "\u0000" llegaba a la
 * base y respondia 500 (D-16, lo encontro el escaneo activo de OWASP ZAP). Ningun dato real lo
 * necesita, asi que se corta en la puerta para todos los endpoints.
 */
@Provider
@PreMatching
public class CaracterNulFilter implements ContainerRequestFilter {

    static final String MENSAJE = "El pedido tiene caracteres no válidos.";

    @Override
    public void filter(ContainerRequestContext request) throws IOException {
        String uri = request.getUriInfo().getRequestUri().getRawPath() + "?" + request.getUriInfo().getRequestUri().getRawQuery();
        if (uri.contains("%00")) {
            request.abortWith(ApiError.respuesta(Response.Status.BAD_REQUEST, MENSAJE));
            return;
        }
        if (!request.hasEntity()) {
            return;
        }
        byte[] cuerpo = request.getEntityStream().readAllBytes();
        if (tieneNul(cuerpo)) {
            request.abortWith(ApiError.respuesta(Response.Status.BAD_REQUEST, MENSAJE));
            return;
        }
        // El cuerpo ya se leyo: se vuelve a poner para que lo lea JAX-RS.
        request.setEntityStream(new ByteArrayInputStream(cuerpo));
    }

    /** El byte 0 o su escape en JSON ("\u0000", con mayusculas o minusculas). */
    static boolean tieneNul(byte[] cuerpo) {
        for (byte b : cuerpo) {
            if (b == 0) {
                return true;
            }
        }
        return new String(cuerpo, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).contains("\\u0000");
    }
}
