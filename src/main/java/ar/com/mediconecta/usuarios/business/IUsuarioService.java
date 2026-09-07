package ar.com.mediconecta.usuarios.business;

import ar.com.mediconecta.usuarios.model.Usuario;

public interface IUsuarioService {
    Usuario registrarUsuario(Usuario usuario);
    Usuario buscarUsuario(Long id);
    boolean existeUsuario(Long id);
    Usuario autenticar(String email, String password);
}