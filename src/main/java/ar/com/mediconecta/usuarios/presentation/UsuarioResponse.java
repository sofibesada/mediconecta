package ar.com.mediconecta.usuarios.presentation;

import ar.com.mediconecta.usuarios.model.Usuario;

public class UsuarioResponse {
    public Long id;
    public String nombre;
    public String email;
    public String rol;
    public boolean debeCambiarPassword;
    public boolean activo;
    public String obraSocial;
    public String numeroAfiliado;
    public String planObraSocial;
    public Integer copagoPorcentaje;
    /** Solo viene al iniciar sesion (login, registro, cambio de contrasena). */
    public String token;

    public static UsuarioResponse from(Usuario usuario) {
        UsuarioResponse response = new UsuarioResponse();
        response.id = usuario.getId();
        response.nombre = usuario.getNombre();
        response.email = usuario.getEmail();
        response.rol = usuario.getRol() != null ? usuario.getRol().name() : null;
        response.debeCambiarPassword = usuario.isDebeCambiarPassword();
        response.activo = usuario.isActivo();
        response.obraSocial = usuario.getObraSocial();
        response.numeroAfiliado = usuario.getNumeroAfiliado();
        response.planObraSocial = usuario.getPlanObraSocial();
        response.copagoPorcentaje = usuario.getCopagoPorcentaje();
        return response;
    }
}
