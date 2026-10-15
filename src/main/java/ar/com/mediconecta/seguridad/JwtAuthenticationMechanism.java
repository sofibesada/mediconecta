package ar.com.mediconecta.seguridad;

import ar.com.mediconecta.config.CorsFilter;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import ar.com.mediconecta.usuarios.model.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.CallerPrincipal;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

/**
 * Mecanismo de autenticacion de Jakarta Security: el contenedor lo llama en CADA pedido,
 * antes de aplicar las restricciones de web.xml y los @RolesAllowed.
 *
 * Lee el header "Authorization: Bearer <token>", valida el token y le informa al
 * contenedor quien es el usuario (principal = su id) y su rol (grupo PACIENTE,
 * PROFESIONAL o ADMIN). A partir de ahi la seguridad declarativa funciona sola:
 * web.xml, @RolesAllowed en los EJB y SecurityContext en el codigo.
 *
 * Un usuario con contrasena temporal se autentica SIN rol: solo puede entrar a lo que
 * pide "cualquier usuario autenticado" (cambiar-password, ver web.xml).
 */
@ApplicationScoped
public class JwtAuthenticationMechanism implements HttpAuthenticationMechanism {

    private static final String PREFIJO = "Bearer ";

    @Inject
    private TokenService tokenService;

    @Inject
    private IUsuarioService usuarioService;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request, HttpServletResponse response,
                                                HttpMessageContext contexto) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIJO)) {
            // Sin token: anonimo. Si el recurso es protegido, el contenedor responde 401/403.
            return contexto.isProtected() ? rechazar(request, response, "Tenés que iniciar sesión") : contexto.doNothing();
        }
        Long usuarioId = tokenService.validar(header.substring(PREFIJO.length()));
        Usuario usuario = usuarioId != null ? usuarioService.buscarParaSesion(usuarioId) : null;
        if (usuario == null) {
            return contexto.isProtected()
                    ? rechazar(request, response, "Tu sesión venció o tu cuenta fue desactivada. Volvé a iniciar sesión.")
                    : contexto.doNothing();
        }
        Set<String> grupos = usuario.isDebeCambiarPassword() ? Set.of() : Set.of(usuario.getRol().name());
        return contexto.notifyContainerAboutLogin(new CallerPrincipal(String.valueOf(usuario.getId())), grupos);
    }

    private AuthenticationStatus rechazar(HttpServletRequest request, HttpServletResponse response, String mensaje) {
        CorsFilter.aplicar(request.getHeader("Origin"), response::setHeader);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write("{\"error\":\"" + mensaje + "\"}");
            // Se envia ya: si no, WildFly reemplaza el cuerpo por su pagina de error HTML.
            response.flushBuffer();
        } catch (IOException ignored) {
            // el cliente corto la conexion; el 401 ya esta puesto
        }
        return AuthenticationStatus.SEND_FAILURE;
    }
}
