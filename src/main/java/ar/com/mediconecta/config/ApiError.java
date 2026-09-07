package ar.com.mediconecta.config;

/**
 * Cuerpo JSON uniforme para los errores de la API: {"error": "..."}.
 */
public class ApiError {
    public String error;

    public ApiError() {}

    public ApiError(String error) {
        this.error = error;
    }
}
