package ar.com.mediconecta.idempotencia.business;

import jakarta.ejb.ApplicationException;

/**
 * Llegaron dos pedidos simultaneos con la misma Idempotency-Key y el otro termino primero:
 * este se revierte y responde 409. Al reintentar con la misma clave se recibe la respuesta
 * del que se guardo.
 */
@ApplicationException(rollback = true)
public class PedidoEnCursoException extends IllegalStateException {
    public PedidoEnCursoException() {
        super("Ya hay un pedido igual en curso con esa Idempotency-Key. Reintentá en unos segundos.");
    }
}
