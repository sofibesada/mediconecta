package ar.com.mediconecta.usuarios.business;

import ar.com.mediconecta.usuarios.data.UsuarioRepository;
import ar.com.mediconecta.usuarios.model.Usuario;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

@Stateless
public class UsuarioService implements IUsuarioService {

    @Inject
    private UsuarioRepository usuarioRepository;

    @Override
    public Usuario registrarUsuario(Usuario usuario) {
        return usuarioRepository.guardar(usuario);
    }

    @Override
    public Usuario buscarUsuario(Long id) {
        return usuarioRepository.buscarPorId(id);
    }

    @Override
    public Usuario autenticar(String email, String password) {
        return null; // lo implementamos en la próxima entrega
    }
}