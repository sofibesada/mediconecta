package ar.com.mediconecta.seguridad;

import ar.com.mediconecta.config.ApiError;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.SecurityContext;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.security.Principal;

/**
 * Quien hace el pedido, segun el token validado por JwtAuthenticationMechanism.
 *
 * Los recursos REST toman el id del usuario de aca y NUNCA de un parametro que manda
 * el cliente: antes bastaba con cambiar ?usuarioId= para leer datos de otro usuario.
 */
@RequestScoped
public class UsuarioActual {

    @Inject
    private SecurityContext securityContext;

    public Long id() {
        Principal p = securityContext.getCallerPrincipal();
        if (p == null) {
            throw new NotAuthorizedException(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ApiError("Tenés que iniciar sesión")).type(MediaType.APPLICATION_JSON).build());
        }
        return Long.valueOf(p.getName());
    }

    public boolean es(String rol) {
        return securityContext.isCallerInRole(rol);
    }

    public boolean esElMismo(Long usuarioId) {
        return id().equals(usuarioId);
    }

    /** 403 si el recurso no es del usuario actual (y no es admin, cuando se permite). */
    public void exigir(boolean permitido, String mensaje) {
        if (!permitido) {
            throw prohibido(mensaje);
        }
    }

    public static ForbiddenException prohibido(String mensaje) {
        return new ForbiddenException(Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(mensaje)).type(MediaType.APPLICATION_JSON).build());
    }
}
