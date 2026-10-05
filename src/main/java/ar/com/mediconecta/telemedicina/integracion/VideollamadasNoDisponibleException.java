package ar.com.mediconecta.telemedicina.integracion;

/**
 * La plataforma de videollamadas no respondió a tiempo o devolvió un error.
 *
 * Es una excepcion de SISTEMA (no @ApplicationException) a proposito: si sale del MDB,
 * el contenedor hace rollback de su transaccion y el broker reentrega el mensaje
 * (3 intentos y despues a la DLQ, segun el address-setting del topico).
 */
public class VideollamadasNoDisponibleException extends RuntimeException {
    public VideollamadasNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
