package ar.com.mediconecta.usuarios.business;

import ar.com.mediconecta.notificaciones.business.INotificacionService;
import ar.com.mediconecta.seguridad.Roles;
import ar.com.mediconecta.usuarios.data.UsuarioRepository;
import ar.com.mediconecta.usuarios.model.RolUsuario;
import ar.com.mediconecta.usuarios.model.Usuario;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Pedido de recuperacion de contrasena al administrador. Las contrasenas no se pueden
 * "recuperar" (se guardan con hash BCrypt): el administrador genera una temporal y el
 * usuario la cambia al ingresar.
 *
 * - Desde la pantalla de ingreso, con el email: la respuesta es SIEMPRE la misma, exista o
 *   no la cuenta, para no revelar que emails estan registrados (enumeracion de usuarios).
 * - Desde el perfil, con la sesion iniciada.
 *
 * A cada administrador le llega una notificacion, y en Administracion el usuario figura con
 * el pedido pendiente hasta que se le genera la contrasena temporal. Para evitar abusos, se
 * acepta un pedido por cuenta cada 10 minutos.
 */
@Stateless
public class RecuperacionPasswordService {

    private static final Logger LOG = Logger.getLogger(RecuperacionPasswordService.class.getName());
    static final long MINUTOS_ENTRE_PEDIDOS = 10;

    @Inject
    private UsuarioRepository usuarioRepository;

    @Inject
    private INotificacionService notificacionService;

    @PermitAll
    public void solicitarPorEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Ingresá tu email");
        }
        solicitar(usuarioRepository.buscarPorEmail(email.trim()));
    }

    @RolesAllowed({Roles.PACIENTE, Roles.PROFESIONAL})
    public void solicitarDesdePerfil(Long usuarioId) {
        solicitar(usuarioRepository.buscarPorId(usuarioId));
    }

    private void solicitar(Usuario u) {
        // Cuenta inexistente, desactivada o de administrador: no se hace nada, sin avisar.
        if (u == null || !u.isActivo() || u.getRol() == RolUsuario.ADMIN) {
            return;
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (u.getRecuperacionSolicitadaEn() != null
                && u.getRecuperacionSolicitadaEn().isAfter(ahora.minusMinutes(MINUTOS_ENTRE_PEDIDOS))) {
            return; // ya hay un pedido reciente
        }
        u.setRecuperacionSolicitadaEn(ahora);
        usuarioRepository.actualizar(u);

        String rol = u.getRol() == RolUsuario.PROFESIONAL ? "profesional" : "paciente";
        for (Usuario admin : usuarioRepository.listarPorRol(RolUsuario.ADMIN)) {
            notificacionService.avisar(admin.getId(), "RECUPERAR_PASSWORD", "Pedido de recuperación de contraseña",
                    u.getNombre() + " (" + rol + ", " + u.getEmail() + ") pidió recuperar su contraseña. "
                            + "Generale una contraseña temporal desde Administración.");
        }
        LOG.info("[Recuperacion] usuario #" + u.getId() + " pidio recuperar la contrasena");
    }
}
