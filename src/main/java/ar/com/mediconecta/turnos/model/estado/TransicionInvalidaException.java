package ar.com.mediconecta.turnos.model.estado;

import jakarta.ejb.ApplicationException;

/**
 * La operacion no se puede hacer en el estado actual del turno (por ejemplo, confirmar un
 * turno cancelado). La API la traduce a 409 Conflict. Es de aplicacion y sin rollback:
 * un cambio que el servicio ya guardo antes (como liberar una reserva vencida) se conserva.
 */
@ApplicationException(rollback = false)
public class TransicionInvalidaException extends IllegalStateException {
    public TransicionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
