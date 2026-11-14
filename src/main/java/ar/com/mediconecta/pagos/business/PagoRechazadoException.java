package ar.com.mediconecta.pagos.business;

import jakarta.ejb.ApplicationException;

/**
 * La pasarela rechazo el cobro (fondos insuficientes, rechazo del banco). rollback = false:
 * el intento rechazado SI queda registrado, para que se vea por que no se cobro. La API
 * responde 402 Payment Required con el motivo.
 */
@ApplicationException(rollback = false)
public class PagoRechazadoException extends RuntimeException {
    public PagoRechazadoException(String motivo) {
        super("El pago fue rechazado: " + (motivo == null ? "consultá con tu banco" : motivo));
    }
}
