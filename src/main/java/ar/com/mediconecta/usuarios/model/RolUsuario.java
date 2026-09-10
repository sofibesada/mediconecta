package ar.com.mediconecta.usuarios.model;

/**
 * Rol de un usuario dentro de MediConecta.
 * - PACIENTE: reserva y confirma turnos, consulta su historia clinica.
 * - PROFESIONAL: publica su disponibilidad y atiende su agenda.
 * - ADMIN: administra usuarios y turnos. No se crea por el registro publico.
 */
public enum RolUsuario {
    PACIENTE,
    PROFESIONAL,
    ADMIN
}
