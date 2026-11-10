package ar.com.mediconecta.seguridad;

import ar.com.mediconecta.config.ApiError;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.security.Principal;
import java.util.logging.Logger;

/**
 * Quien hace el pedido, segun el token validado por JwtAuthenticationMechanism.
 *
 * Los recursos REST toman el id del usuario de aca y NUNCA de un parametro que manda
 * el cliente: antes bastaba con cambiar ?usuarioId= para leer datos de otro usuario.
 */
@RequestScoped
public class UsuarioActual {

    private static final Logger LOG = Logger.getLogger(UsuarioActual.class.getName());

    @Inject
    private SecurityContext securityContext;

    @Inject
    private HttpServletRequest request;

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

    /**
     * Si el recurso no es del usuario actual responde 404, igual que si no existiera: asi
     * no se confirma que el id existe (BOLA). El motivo queda en el log, solo con ids.
     */
    public void exigir(boolean permitido, String motivo) {
        if (!permitido) {
            LOG.warning("[Seguridad] Acceso rechazado: usuario #" + id() + " -> " + request.getMethod() + " "
                    + request.getRequestURI() + " (" + motivo + ")");
            throw noEncontrado();
        }
    }

    public static NotFoundException noEncontrado() {
        return new NotFoundException(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError("No encontrado")).type(MediaType.APPLICATION_JSON).build());
    }
}
