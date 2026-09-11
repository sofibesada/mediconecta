package ar.com.mediconecta.turnos.business;

import jakarta.ejb.ApplicationException;

/**
 * Datos invalidos para una operacion de turnos (ej. profesionalId que no
 * corresponde a un usuario con rol PROFESIONAL). Ver ConflictoTurnoException
 * para por que rollback = false.
 */
@ApplicationException(rollback = false)
public class DatosTurnoInvalidosException extends IllegalArgumentException {
    public DatosTurnoInvalidosException(String message) {
        super(message);
    }
}
