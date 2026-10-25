package ar.com.mediconecta.obrassociales.business;

import jakarta.ejb.ApplicationException;

/**
 * La obra social no autorizo la consulta, o no respondio, al confirmar un turno.
 *
 * Es una excepcion de APLICACION con rollback = true: llega tal cual al cliente (no se
 * envuelve en EJBException) y ademas el contenedor revierte toda la transaccion de la
 * confirmacion: el turno no queda confirmado, no se publica el evento en el topico ni
 * se manda nada a la cola de facturacion. La auditoria del intento queda, porque se
 * guarda en otra transaccion (REQUIRES_NEW).
 */
@ApplicationException(rollback = true)
public class AutorizacionRechazadaException extends IllegalStateException {

    public AutorizacionRechazadaException(String mensaje) {
        super(mensaje);
    }
}
