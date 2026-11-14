package ar.com.mediconecta.usuarios.business;

/**
 * Datos que cada usuario puede cambiar de su propia cuenta.
 *
 * @param nombre          nombre visible
 * @param email           email de ingreso; cambiarlo exige la contrasena actual
 * @param especialidad    solo para profesionales (se ignora para los demas roles)
 * @param passwordActual  obligatoria solo si cambia el email
 */
public record DatosPerfil(String nombre, String email, String especialidad, String passwordActual) {}
