package ar.com.mediconecta.usuarios.business;

import ar.com.mediconecta.usuarios.data.UsuarioRepository;
import ar.com.mediconecta.usuarios.model.RolUsuario;
import ar.com.mediconecta.usuarios.model.Usuario;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import org.mindrot.jbcrypt.BCrypt;
import jakarta.annotation.security.RolesAllowed;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@Stateless
public class UsuarioService implements IUsuarioService {

    private static final Logger LOG = Logger.getLogger(UsuarioService.class.getName());

    @Inject
    private UsuarioRepository usuarioRepository;

    @PostConstruct
    public void init() {
        LOG.info("[Ciclo de vida] UsuarioService @Stateless CREADO por WildFly (entra al pool) - instancia #" + System.identityHashCode(this));
    }

    @PreDestroy
    public void destroy() {
        LOG.info("[Ciclo de vida] UsuarioService @Stateless DESTRUIDO por WildFly (sale del pool) - instancia #" + System.identityHashCode(this));
    }

    @Override
    @PermitAll
    public Usuario registrarUsuario(Usuario usuario) {
        // El registro publico solo crea PACIENTE. Profesionales los da de alta
        // un ADMIN; ADMIN se siembra desde el backend.
        usuario.setRol(RolUsuario.PACIENTE);
        usuario.setDebeCambiarPassword(false);
        usuario.setPassword(BCrypt.hashpw(usuario.getPassword(), BCrypt.gensalt()));
        return usuarioRepository.guardar(usuario);
    }

    @Override
    @PermitAll
    public Usuario crearProfesional(Long adminId, Usuario datos) {
        if (!esAdmin(adminId)) {
            throw new IllegalArgumentException("Solo un administrador puede crear profesionales");
        }
        if (datos.getNombre() == null || datos.getNombre().isBlank()
                || datos.getEmail() == null || datos.getEmail().isBlank()
                || datos.getPassword() == null || datos.getPassword().isBlank()) {
            throw new IllegalArgumentException("Nombre, email y contrasena temporal son obligatorios");
        }
        if (usuarioRepository.buscarPorEmail(datos.getEmail()) != null) {
            throw new IllegalStateException("Ya existe un usuario con ese email");
        }
        Usuario prof = new Usuario();
        prof.setNombre(datos.getNombre());
        prof.setEmail(datos.getEmail());
        prof.setRol(RolUsuario.PROFESIONAL);
        prof.setDebeCambiarPassword(true);
        prof.setPassword(BCrypt.hashpw(datos.getPassword(), BCrypt.gensalt()));
        return usuarioRepository.guardar(prof);
    }

    @Override
    @PermitAll
    public Usuario cambiarPassword(Long usuarioId, String passwordActual, String passwordNueva) {
        Usuario u = usuarioRepository.buscarPorId(usuarioId);
        if (u == null) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }
        if (passwordActual == null || !BCrypt.checkpw(passwordActual, u.getPassword())) {
            throw new IllegalArgumentException("La contrasena actual es incorrecta");
        }
        if (passwordNueva == null || passwordNueva.length() < 4) {
            throw new IllegalArgumentException("La contrasena nueva debe tener al menos 4 caracteres");
        }
        if (BCrypt.checkpw(passwordNueva, u.getPassword())) {
            throw new IllegalArgumentException("La contrasena nueva no puede ser igual a la actual");
        }
        u.setPassword(BCrypt.hashpw(passwordNueva, BCrypt.gensalt()));
        u.setDebeCambiarPassword(false);
        return usuarioRepository.actualizar(u);
    }

    @Override
    @PermitAll
    public Usuario cambiarEstadoUsuario(Long adminId, Long usuarioId, boolean activo) {
        if (!esAdmin(adminId)) {
            throw new IllegalArgumentException("Solo un administrador puede activar o desactivar usuarios");
        }
        Usuario u = usuarioRepository.buscarPorId(usuarioId);
        if (u == null) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }
        if (u.getRol() == RolUsuario.ADMIN) {
            throw new IllegalStateException("No se puede desactivar una cuenta de administrador");
        }
        u.setActivo(activo);
        return usuarioRepository.actualizar(u);
    }

    @Override
    @PermitAll
    public String resetearPassword(Long adminId, Long usuarioId) {
        if (!esAdmin(adminId)) {
            throw new IllegalArgumentException("Solo un administrador puede resetear contrasenas");
        }
        Usuario u = usuarioRepository.buscarPorId(usuarioId);
        if (u == null) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }
        if (u.getRol() == RolUsuario.ADMIN) {
            throw new IllegalStateException("La cuenta de administrador se administra desde el backend");
        }
        String temporal = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        u.setPassword(BCrypt.hashpw(temporal, BCrypt.gensalt()));
        u.setDebeCambiarPassword(true);
        usuarioRepository.actualizar(u);
        return temporal;
    }

    @Override
    @PermitAll
    public void sembrarAdmin(String nombre, String email, String password) {
        if (usuarioRepository.buscarPorEmail(email) != null) {
            return;
        }
        Usuario admin = new Usuario();
        admin.setNombre(nombre);
        admin.setEmail(email);
        admin.setRol(RolUsuario.ADMIN);
        admin.setDebeCambiarPassword(false);
        admin.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        usuarioRepository.guardar(admin);
        LOG.info("[Seed] Cuenta ADMIN creada: " + email);
    }

    @Override
    @RolesAllowed("admin")
    public Usuario buscarUsuario(Long id) {
        return usuarioRepository.buscarPorId(id);
    }

    @Override
    @PermitAll
    public boolean existeUsuario(Long id) {
        return usuarioRepository.buscarPorId(id) != null;
    }

    @Override
    @PermitAll
    public boolean esProfesional(Long id) {
        Usuario u = usuarioRepository.buscarPorId(id);
        return u != null && u.getRol() == RolUsuario.PROFESIONAL;
    }

    @Override
    @PermitAll
    public boolean esAdmin(Long id) {
        Usuario u = id != null ? usuarioRepository.buscarPorId(id) : null;
        return u != null && u.getRol() == RolUsuario.ADMIN;
    }

    @Override
    @PermitAll
    public String nombreDe(Long id) {
        if (id == null) {
            return null;
        }
        Usuario u = usuarioRepository.buscarPorId(id);
        return u != null ? u.getNombre() : null;
    }

    @Override
    @PermitAll
    public List<Usuario> listarProfesionales() {
        return usuarioRepository.listarPorRol(RolUsuario.PROFESIONAL).stream()
                .filter(Usuario::isActivo)
                .toList();
    }

    @Override
    @PermitAll
    public List<Usuario> listarTodos() {
        return usuarioRepository.listarTodos();
    }

    @Override
    @PermitAll
    public Usuario autenticar(String email, String password) {
        Usuario usuario = usuarioRepository.buscarPorEmail(email);
        if (usuario != null && BCrypt.checkpw(password, usuario.getPassword())) {
            return usuario;
        }
        return null;
    }
}
