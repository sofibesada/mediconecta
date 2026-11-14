package ar.com.mediconecta.pagos.integracion;

/**
 * La pasarela de pagos no respondio (caida, timeout o error 5xx). Es una excepcion de
 * SISTEMA a proposito: revierte la transaccion (no queda ningun pago a medias) y la API
 * responde 503.
 */
public class PasarelaNoDisponibleException extends RuntimeException {
    public PasarelaNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
