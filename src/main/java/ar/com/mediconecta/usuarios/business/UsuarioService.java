package ar.com.mediconecta.usuarios.business;

import ar.com.mediconecta.seguridad.Roles;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
        if (usuario.getNombre() == null || usuario.getNombre().isBlank()
                || usuario.getEmail() == null || usuario.getEmail().isBlank()
                || usuario.getPassword() == null || usuario.getPassword().isBlank()) {
            throw new IllegalArgumentException("Nombre, email y contraseña son obligatorios");
        }
        if (usuarioRepository.buscarPorEmail(usuario.getEmail()) != null) {
            throw new IllegalStateException("El correo ya está registrado");
        }
        usuario.setRol(RolUsuario.PACIENTE);
        usuario.setDebeCambiarPassword(false);
        usuario.setPassword(BCrypt.hashpw(usuario.getPassword(), BCrypt.gensalt()));
        return usuarioRepository.guardar(usuario);
    }

    @Override
    @RolesAllowed(Roles.ADMIN)
    public Usuario crearProfesional(Long adminId, Usuario datos) {
        exigirAdmin(adminId, "Solo un administrador puede crear profesionales");
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
        prof.setEspecialidad(Especialidades.validar(datos.getEspecialidad()));
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
        if (!u.isActivo()) {
            throw new IllegalStateException("Tu cuenta está desactivada. Contactá al administrador.");
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
    @RolesAllowed(Roles.ADMIN)
    public Usuario cambiarEstadoUsuario(Long adminId, Long usuarioId, boolean activo) {
        exigirAdmin(adminId, "Solo un administrador puede activar o desactivar usuarios");
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
    @RolesAllowed(Roles.ADMIN)
    public String resetearPassword(Long adminId, Long usuarioId) {
        exigirAdmin(adminId, "Solo un administrador puede resetear contrasenas");
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
        u.setRecuperacionSolicitadaEn(null); // si habia un pedido de recuperacion, queda atendido
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
    @RolesAllowed(Roles.ADMIN)
    public Usuario buscarUsuario(Long id) {
        return usuarioRepository.buscarPorId(id);
    }

    @Override
    @PermitAll
    public Usuario buscarParaSesion(Long id) {
        Usuario u = id != null ? usuarioRepository.buscarPorId(id) : null;
        return u != null && u.isActivo() ? u : null;
    }

    @Override
    @RolesAllowed(Roles.PACIENTE)
    public Usuario guardarCobertura(Long pacienteId, String obraSocial, String numeroAfiliado, String plan, int copagoPorcentaje) {
        Usuario u = usuarioRepository.buscarPorId(pacienteId);
        u.setObraSocial(obraSocial);
        u.setNumeroAfiliado(numeroAfiliado);
        u.setPlanObraSocial(plan);
        u.setCopagoPorcentaje(copagoPorcentaje);
        return usuarioRepository.actualizar(u);
    }

    @Override
    @RolesAllowed(Roles.PACIENTE)
    public Usuario quitarCobertura(Long pacienteId) {
        Usuario u = usuarioRepository.buscarPorId(pacienteId);
        u.setObraSocial(null);
        u.setNumeroAfiliado(null);
        u.setPlanObraSocial(null);
        u.setCopagoPorcentaje(null);
        return usuarioRepository.actualizar(u);
    }

    @Override
    @PermitAll
    public boolean existeUsuario(Long id) {
        // "Existe" implica activo: una cuenta desactivada no debe poder
        // seguir agendando/publicando turnos con solo tener el id guardado
        // en el navegador.
        Usuario u = id != null ? usuarioRepository.buscarPorId(id) : null;
        return u != null && u.isActivo();
    }

    @Override
    @PermitAll
    public boolean esProfesional(Long id) {
        Usuario u = usuarioRepository.buscarPorId(id);
        return u != null && u.getRol() == RolUsuario.PROFESIONAL && u.isActivo();
    }

    @Override
    @PermitAll
    public boolean esAdmin(Long id) {
        Usuario u = id != null ? usuarioRepository.buscarPorId(id) : null;
        return u != null && u.getRol() == RolUsuario.ADMIN;
    }

    @Override
    @PermitAll
    public void exigirAdmin(Long adminId, String mensaje) {
        if (!esAdmin(adminId)) {
            throw new IllegalArgumentException(mensaje);
        }
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
    @RolesAllowed(Roles.ADMIN)
    public List<Usuario> listarTodos() {
        return usuarioRepository.listarTodos();
    }

    @Override
    @PermitAll
    public Map<Long, String> nombresDe(Set<Long> ids) {
        Set<Long> idsLimpios = new HashSet<>();
        for (Long id : ids) {
            if (id != null) {
                idsLimpios.add(id);
            }
        }
        if (idsLimpios.isEmpty()) {
            return Map.of();
        }
        return usuarioRepository.listarPorIds(idsLimpios).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));
    }

    @Override
    @RolesAllowed({Roles.PACIENTE, Roles.PROFESIONAL, Roles.ADMIN})
    public Usuario actualizarPerfil(Long usuarioId, DatosPerfil datos) {
        Usuario u = usuarioRepository.buscarPorId(usuarioId);
        if (u == null || !u.isActivo()) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }
        if (datos.nombre() == null || datos.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (datos.nombre().trim().length() > 80) {
            throw new IllegalArgumentException("El nombre puede tener hasta 80 caracteres");
        }
        String email = datos.email() == null ? "" : datos.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("El email no es válido");
        }
        if (!email.equalsIgnoreCase(u.getEmail())) {
            // Cambiar el email cambia con que se ingresa: se pide la contrasena para confirmar.
            if (datos.passwordActual() == null || !BCrypt.checkpw(datos.passwordActual(), u.getPassword())) {
                throw new IllegalArgumentException("Para cambiar el email ingresá tu contraseña actual");
            }
            if (usuarioRepository.buscarPorEmail(email) != null) {
                throw new IllegalStateException("Ya existe una cuenta con ese email");
            }
            u.setEmail(email);
        }
        u.setNombre(datos.nombre().trim());
        if (u.getRol() == RolUsuario.PROFESIONAL) {
            u.setEspecialidad(Especialidades.validar(datos.especialidad()));
        }
        return usuarioRepository.actualizar(u);
    }

    @Override
    @PermitAll
    public Map<Long, String> especialidadesDe(Set<Long> ids) {
        Set<Long> limpios = new HashSet<>();
        for (Long id : ids) {
            if (id != null) {
                limpios.add(id);
            }
        }
        if (limpios.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> r = new java.util.HashMap<>();
        for (Usuario u : usuarioRepository.listarPorIds(limpios)) {
            if (u.getEspecialidad() != null) {
                r.put(u.getId(), u.getEspecialidad());
            }
        }
        return r;
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
