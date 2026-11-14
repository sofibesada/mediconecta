package ar.com.mediconecta.usuarios.business;

import java.util.List;

/**
 * Especialidades medicas con las que trabaja la plataforma. Es la unica lista: la usan el
 * alta de profesionales, el perfil y el filtro de busqueda del frontend
 * (GET /profesionales/especialidades).
 */
public final class Especialidades {

    public static final String POR_DEFECTO = "Clínica médica";

    public static final List<String> LISTA = List.of(
            "Cardiología",
            "Clínica médica",
            "Dermatología",
            "Endocrinología",
            "Gastroenterología",
            "Ginecología",
            "Kinesiología",
            "Neurología",
            "Nutrición",
            "Oftalmología",
            "Otorrinolaringología",
            "Pediatría",
            "Psicología",
            "Psiquiatría",
            "Traumatología",
            "Urología");

    private Especialidades() {}

    /** La especialidad tal como figura en la lista, o una excepcion si no existe. */
    public static String validar(String especialidad) {
        if (especialidad == null || especialidad.isBlank()) {
            return POR_DEFECTO;
        }
        return LISTA.stream()
                .filter(e -> e.equalsIgnoreCase(especialidad.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Especialidad inexistente: " + especialidad));
    }
}
