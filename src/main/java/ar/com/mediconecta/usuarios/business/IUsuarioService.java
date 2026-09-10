package ar.com.mediconecta.usuarios.business;

import ar.com.mediconecta.usuarios.model.Usuario;
import java.util.List;

public interface IUsuarioService {
    Usuario registrarUsuario(Usuario usuario);
    Usuario buscarUsuario(Long id);
    boolean existeUsuario(Long id);
    boolean esProfesional(Long id);
    String nombreDe(Long id);
    List<Usuario> listarProfesionales();
    Usuario autenticar(String email, String password);
}
