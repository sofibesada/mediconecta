package ar.com.mediconecta.pagos.business;

import jakarta.ejb.ApplicationException;

/** Hay un cobro de este turno con resultado todavia incierto: no se cobra otro (409). */
@ApplicationException(rollback = true)
public class PagoEnCursoException extends IllegalStateException {
    public PagoEnCursoException() {
        super("Hay un pago de este turno que todavía se está procesando. Esperá un minuto y revisá si quedó pagado antes de volver a intentar.");
    }
}
