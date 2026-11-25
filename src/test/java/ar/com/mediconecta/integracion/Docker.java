package ar.com.mediconecta.integracion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Comandos de docker para las pruebas contra el sistema levantado (simular fallas, preparar
 * datos). Se pasan los argumentos sin shell: así funcionan igual en Windows, Linux y el CI.
 */
final class Docker {

    record Resultado(int codigo, String salida) {
        boolean ok() {
            return codigo == 0;
        }
    }

    private Docker() {
    }

    static Resultado ejecutar(String... args) throws IOException, InterruptedException {
        return conEntrada(null, args);
    }

    /** Ejecuta docker con un texto en la entrada estándar (por ejemplo, SQL para psql). */
    static Resultado conEntrada(String entrada, String... args) throws IOException, InterruptedException {
        String[] comando = new String[args.length + 1];
        comando[0] = "docker";
        System.arraycopy(args, 0, comando, 1, args.length);
        Process p;
        try {
            p = new ProcessBuilder(comando).redirectErrorStream(true).start();
        } catch (IOException e) {
            return new Resultado(-1, e.getMessage());
        }
        try (var in = p.getOutputStream()) {
            if (entrada != null) {
                in.write(entrada.getBytes(StandardCharsets.UTF_8));
            }
        }
        String salida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return new Resultado(-1, "docker no respondió");
        }
        return new Resultado(p.exitValue(), salida);
    }

    /** Variable de entorno de un servicio del docker compose ("" si no está). */
    static String variable(String servicio, String nombre) throws IOException, InterruptedException {
        Resultado r = ejecutar("compose", "exec", "-T", servicio, "printenv", nombre);
        return r.ok() ? r.salida().trim() : "";
    }

    /** Agrega (o reemplaza) una system property de WildFly en caliente. */
    static void propiedad(String nombre, String valor) throws IOException, InterruptedException {
        sinPropiedad(nombre);
        jbossCli("/system-property=" + nombre + ":add(value=" + valor + ")");
    }

    static void sinPropiedad(String nombre) throws IOException, InterruptedException {
        jbossCli("/system-property=" + nombre + ":remove");
    }

    private static void jbossCli(String comando) throws IOException, InterruptedException {
        ejecutar("compose", "exec", "-T", "app", "/opt/jboss/wildfly/bin/jboss-cli.sh", "--connect", "--command=" + comando);
    }
}
