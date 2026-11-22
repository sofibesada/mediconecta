package ar.com.mediconecta.ia.business;

/** El proveedor de IA no respondio, rechazo la consulta o fallo la red. El gateway usa el respaldo. */
public class ProveedorIAException extends RuntimeException {

    public ProveedorIAException(String mensaje) {
        super(mensaje);
    }

    public ProveedorIAException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
