package ar.com.mediconecta.idempotencia.business;

import jakarta.ejb.ApplicationException;

/** La misma Idempotency-Key se mando para otra operacion: 422, sin ejecutar nada. */
@ApplicationException(rollback = true)
public class ClaveReutilizadaException extends RuntimeException {
    public ClaveReutilizadaException() {
        super("Esa Idempotency-Key ya se usó para otra operación. Generá una clave nueva.");
    }
}
