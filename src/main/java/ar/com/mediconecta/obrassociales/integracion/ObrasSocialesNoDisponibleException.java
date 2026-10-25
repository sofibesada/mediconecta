package ar.com.mediconecta.obrassociales.integracion;

/**
 * El sistema legado de obras sociales no respondio (caido, timeout o SOAP Fault).
 *
 * Es una excepcion de SISTEMA (no @ApplicationException) a proposito: si sale del
 * consumidor de la cola de facturacion, el contenedor hace rollback y Artemis reentrega
 * el mensaje. Al confirmar un turno, TurnoService la traduce a un error de negocio.
 */
public class ObrasSocialesNoDisponibleException extends RuntimeException {

    public ObrasSocialesNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
