package ar.com.mediconecta.usuarios.business;

import ar.com.mediconecta.usuarios.data.UsuarioRepository;
import ar.com.mediconecta.usuarios.model.Usuario;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import org.mindrot.jbcrypt.BCrypt;
import jakarta.annotation.security.RolesAllowed;

@Stateless
public class UsuarioService implements IUsuarioService {

    @Inject
    private UsuarioRepository usuarioRepository;

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
    public Usuario autenticar(String email, String password) {
        Usuario usuario = usuarioRepository.buscarPorEmail(email);
        if (usuario != null && BCrypt.checkpw(password, usuario.getPassword())) {
            return usuario;
        }
        return null;
    }


}