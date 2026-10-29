package ar.com.mediconecta.config;

/**
 * Configuracion externa. Los secretos (contrasenas, claves, API keys) NUNCA van en el
 * codigo ni en el repositorio: se leen de una system property de WildFly (jboss-cli) o,
 * si no esta, de una variable de entorno (docker compose / .env).
 */
public final class Config {

    private Config() {}

    /** Valor configurado, o null si no hay ninguno. */
    public static String valor(String propiedad, String variableDeEntorno) {
        String v = System.getProperty(propiedad);
        if (v == null || v.isBlank()) {
            v = System.getenv(variableDeEntorno);
        }
        return v == null || v.isBlank() ? null : v;
    }
}
