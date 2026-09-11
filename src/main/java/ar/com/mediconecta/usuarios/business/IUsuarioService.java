package ar.com.mediconecta.usuarios.business;

import ar.com.mediconecta.usuarios.model.Usuario;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IUsuarioService {

    /** Registro publico: siempre crea un PACIENTE. */
    Usuario registrarUsuario(Usuario usuario);

    /** Alta de un profesional hecha por un ADMIN. Queda con contrasena temporal. */
    Usuario crearProfesional(Long adminId, Usuario datos);

    /** Cambio de contrasena; limpia la marca de "debe cambiar". */
    Usuario cambiarPassword(Long usuarioId, String passwordActual, String passwordNueva);

    /** Un ADMIN activa o desactiva a un usuario. Un usuario inactivo no puede ingresar. */
    Usuario cambiarEstadoUsuario(Long adminId, Long usuarioId, boolean activo);

    /** Un ADMIN genera una contrasena temporal nueva; devuelve el texto plano para entregarla. */
    String resetearPassword(Long adminId, Long usuarioId);

    /** Crea la cuenta ADMIN si todavia no existe (sembrado inicial). */
    void sembrarAdmin(String nombre, String email, String password);

    Usuario buscarUsuario(Long id);
    boolean existeUsuario(Long id);
    boolean esProfesional(Long id);
    boolean esAdmin(Long id);
    /** Tira IllegalArgumentException(mensaje) si adminId no es de un ADMIN. */
    void exigirAdmin(Long adminId, String mensaje);
    String nombreDe(Long id);
    /** Nombres de varios usuarios en una sola consulta (evita N+1). */
    Map<Long, String> nombresDe(Set<Long> ids);
    List<Usuario> listarProfesionales();
    List<Usuario> listarTodos();
    Usuario autenticar(String email, String password);
}
