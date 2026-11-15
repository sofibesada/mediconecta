package ar.com.mediconecta.usuarios.presentation;

public class CrearProfesionalRequest {
    public String nombre;
    public String email;
    public String passwordTemporal;
    public String especialidad;
    /** Obras sociales que atiende; si no viene, todas. */
    public java.util.List<String> obrasSociales;
}
