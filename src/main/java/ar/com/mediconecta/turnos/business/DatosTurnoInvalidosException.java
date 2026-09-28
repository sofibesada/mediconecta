package ar.com.mediconecta.turnos.business;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = false)
public class DatosTurnoInvalidosException extends IllegalArgumentException {
    public DatosTurnoInvalidosException(String message) {
        super(message);
    }
}
