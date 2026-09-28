package ar.com.mediconecta.turnos.business;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = false)
public class ConflictoTurnoException extends IllegalStateException {
    public ConflictoTurnoException(String message) {
        super(message);
    }
}
