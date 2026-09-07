package ar.com.mediconecta.usuarios.business;

import ar.com.mediconecta.usuarios.data.UsuarioRepository;
import ar.com.mediconecta.usuarios.model.Usuario;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import org.mindrot.jbcrypt.BCrypt;
import jakarta.annotation.security.RolesAllowed;
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
        String hash = BCrypt.hashpw(usuario.getPassword(), BCrypt.gensalt());
        usuario.setPassword(hash);
        return usuarioRepository.guardar(usuario);
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
    public Usuario autenticar(String email, String password) {
        Usuario usuario = usuarioRepository.buscarPorEmail(email);
        if (usuario != null && BCrypt.checkpw(password, usuario.getPassword())) {
            return usuario;
        }
        return null;
    }


}