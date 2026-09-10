package ar.com.mediconecta.usuarios.business;

import ar.com.mediconecta.usuarios.model.Usuario;
import java.util.List;

public interface IUsuarioService {

    /** Registro publico: siempre crea un PACIENTE. */
    Usuario registrarUsuario(Usuario usuario);

    /** Alta de un profesional hecha por un ADMIN. Queda con contrasena temporal. */
    Usuario crearProfesional(Long adminId, Usuario datos);

    /** Cambio de contrasena; limpia la marca de "debe cambiar". */
    Usuario cambiarPassword(Long usuarioId, String passwordActual, String passwordNueva);

    /** Crea la cuenta ADMIN si todavia no existe (sembrado inicial). */
    void sembrarAdmin(String nombre, String email, String password);

    Usuario buscarUsuario(Long id);
    boolean existeUsuario(Long id);
    boolean esProfesional(Long id);
    boolean esAdmin(Long id);
    String nombreDe(Long id);
    List<Usuario> listarProfesionales();
    List<Usuario> listarTodos();
    Usuario autenticar(String email, String password);
}
