package ar.com.mediconecta.ia.business;

/**
 * Respuesta del Orientador de especialidad.
 *
 * @param especialidad una de Especialidades.LISTA, o null si no corresponde sugerir ninguna
 *                     (urgencia o consulta fuera de tema)
 * @param motivo       explicacion corta para el paciente (texto plano, sin enlaces)
 * @param tipo         que paso con la consulta
 * @param fuente       quien produjo la sugerencia: el proveedor de IA o la regla de respaldo
 * @param aviso        texto fijo que se muestra siempre: la sugerencia no es un diagnostico
 */
public record Orientacion(String especialidad, String motivo, Tipo tipo, Fuente fuente, String aviso) {

    public static final String AVISO = "Es solo una sugerencia para elegir especialidad; no es un diagnóstico médico.";

    public enum Tipo {
        /** Se sugiere una especialidad. */
        SUGERENCIA,
        /** Hay signos de urgencia: se indica la guardia, sin pasar por la IA. */
        URGENCIA,
        /** La consulta no es sobre sintomas o salud. */
        FUERA_DE_TEMA
    }

    public enum Fuente {
        /** Respondio el proveedor configurado (simulado o Claude). */
        IA,
        /** El proveedor fallo, tardo mas de 5 s o respondio algo invalido: regla fija. */
        RESPALDO,
        /** Lo resolvio el gateway sin consultar al proveedor (urgencias). */
        REGLA
    }

    static Orientacion sugerencia(String especialidad, String motivo, Fuente fuente) {
        return new Orientacion(especialidad, motivo, Tipo.SUGERENCIA, fuente, AVISO);
    }
}
