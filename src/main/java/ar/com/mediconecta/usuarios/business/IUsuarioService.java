package ar.com.mediconecta.usuarios.business;

import ar.com.mediconecta.usuarios.model.Usuario;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IUsuarioService {

    Usuario registrarUsuario(Usuario usuario);

    Usuario crearProfesional(Long adminId, Usuario datos);

    Usuario cambiarPassword(Long usuarioId, String passwordActual, String passwordNueva);

    Usuario cambiarEstadoUsuario(Long adminId, Long usuarioId, boolean activo);

    String resetearPassword(Long adminId, Long usuarioId);

    void sembrarAdmin(String nombre, String email, String password);

    Usuario buscarUsuario(Long id);
    boolean existeUsuario(Long id);
    boolean esProfesional(Long id);
    boolean esAdmin(Long id);
    void exigirAdmin(Long adminId, String mensaje);
    String nombreDe(Long id);
    Map<Long, String> nombresDe(Set<Long> ids);
    List<Usuario> listarProfesionales();
    List<Usuario> listarTodos();
    Usuario autenticar(String email, String password);
}
