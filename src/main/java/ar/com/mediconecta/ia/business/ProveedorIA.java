package ar.com.mediconecta.ia.business;

/**
 * Un modelo de lenguaje detras del gateway. El gateway no sabe cual es: hoy hay uno
 * simulado (por defecto, sin costo ni red) y Claude de Anthropic.
 *
 * Debe responder con dos lineas:
 * <pre>
 * ESPECIALIDAD: Dermatología      (o NINGUNA si la consulta no es de salud)
 * MOTIVO: explicacion corta
 * </pre>
 * El gateway valida esa respuesta; nunca la muestra tal cual.
 */
public interface ProveedorIA {

    /** Nombre para el log y la auditoria (no se muestra al paciente). */
    String nombre();

    /**
     * @param instrucciones texto del sistema (lo arma el gateway, el paciente no lo controla)
     * @param consulta      lo que escribio el paciente, ya limpio de datos personales
     * @throws ProveedorIAException si el proveedor no pudo responder
     */
    String responder(String instrucciones, String consulta);
}
