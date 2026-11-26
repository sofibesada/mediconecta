package ar.com.mediconecta.seguridad;

import ar.com.mediconecta.config.Config;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freno contra la fuerza bruta en el login (criterio de la cátedra: 429 ante fuerza bruta).
 *
 * Cuenta los intentos FALLIDOS por cuenta (el email, exista o no: así no revela qué cuentas
 * existen). Con MAX fallidos en la ventana, el ingreso a esa cuenta responde 429 con Retry-After
 * hasta que el más viejo sale de la ventana, aunque llegue la contraseña correcta: si no, el
 * ataque seguiría probando. Un ingreso correcto limpia el contador.
 *
 * Configuración: mediconecta.login.maxFallidos / MEDICONECTA_LOGIN_MAX_FALLIDOS (5) y
 * mediconecta.login.ventanaSegundos / MEDICONECTA_LOGIN_VENTANA_SEGUNDOS (900 = 15 min).
 *
 * Vive en memoria: alcanza para una sola instancia de WildFly. No se limita por IP: detrás de
 * Docker todos los pedidos llegan con la IP del puente y se bloquearía a todos los usuarios.
 */
@ApplicationScoped
public class IntentosDeIngreso {

    static final int MAX_POR_DEFECTO = 5;
    static final long VENTANA_POR_DEFECTO = 900;

    private final Map<String, Deque<Instant>> fallidos = new ConcurrentHashMap<>();
    private final Clock reloj;

    public IntentosDeIngreso() {
        this(Clock.systemUTC());
    }

    IntentosDeIngreso(Clock reloj) {
        this.reloj = reloj;
    }

    /** Lanza IngresoBloqueadoException si la cuenta llegó al máximo de intentos fallidos. */
    public void verificar(String email) {
        Deque<Instant> deCuenta = fallidos.get(clave(email));
        if (deCuenta == null) {
            return;
        }
        Instant ahora = reloj.instant();
        synchronized (deCuenta) {
            purgar(deCuenta, ahora);
            if (deCuenta.size() >= maximo()) {
                long espera = ventana() - (ahora.getEpochSecond() - deCuenta.peekFirst().getEpochSecond());
                throw new IngresoBloqueadoException(Math.max(1, espera));
            }
        }
    }

    public void registrarFallido(String email) {
        Instant ahora = reloj.instant();
        Deque<Instant> deCuenta = fallidos.computeIfAbsent(clave(email), c -> new ArrayDeque<>());
        synchronized (deCuenta) {
            purgar(deCuenta, ahora);
            deCuenta.addLast(ahora);
        }
    }

    /** Ingreso correcto: se olvidan los intentos fallidos de la cuenta. */
    public void limpiar(String email) {
        fallidos.remove(clave(email));
    }

    private void purgar(Deque<Instant> deCuenta, Instant ahora) {
        Instant limite = ahora.minusSeconds(ventana());
        while (!deCuenta.isEmpty() && !deCuenta.peekFirst().isAfter(limite)) {
            deCuenta.pollFirst();
        }
    }

    static String clave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    static int maximo() {
        return entero("mediconecta.login.maxFallidos", "MEDICONECTA_LOGIN_MAX_FALLIDOS", MAX_POR_DEFECTO);
    }

    static long ventana() {
        return entero("mediconecta.login.ventanaSegundos", "MEDICONECTA_LOGIN_VENTANA_SEGUNDOS", (int) VENTANA_POR_DEFECTO);
    }

    private static int entero(String propiedad, String variable, int porDefecto) {
        String valor = Config.valor(propiedad, variable);
        try {
            int n = valor == null ? porDefecto : Integer.parseInt(valor.trim());
            return n >= 1 ? n : porDefecto;
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }
}
