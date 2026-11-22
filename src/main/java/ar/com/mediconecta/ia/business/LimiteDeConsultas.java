package ar.com.mediconecta.ia.business;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ventana deslizante por usuario: como maximo N consultas en los ultimos 60 segundos.
 * Evita que un paciente (o un script con su token) gaste el cupo del proveedor de IA.
 * Vive en memoria del servidor: alcanza para una sola instancia de WildFly.
 */
public class LimiteDeConsultas {

    static final long VENTANA_SEGUNDOS = 60;

    private final int maximo;
    private final Clock reloj;
    private final Map<Long, Deque<Instant>> consultas = new ConcurrentHashMap<>();

    public LimiteDeConsultas(int maximo, Clock reloj) {
        if (maximo < 1) {
            throw new IllegalArgumentException("El límite de consultas tiene que ser al menos 1");
        }
        this.maximo = maximo;
        this.reloj = reloj;
    }

    /** Registra una consulta del usuario o lanza LimiteDeConsultasException si ya llego al maximo. */
    public void registrar(Long usuarioId) {
        Instant ahora = reloj.instant();
        Instant limite = ahora.minusSeconds(VENTANA_SEGUNDOS);
        Deque<Instant> deUsuario = consultas.computeIfAbsent(usuarioId, id -> new ArrayDeque<>());
        synchronized (deUsuario) {
            while (!deUsuario.isEmpty() && !deUsuario.peekFirst().isAfter(limite)) {
                deUsuario.pollFirst();
            }
            if (deUsuario.size() >= maximo) {
                long espera = VENTANA_SEGUNDOS - (ahora.getEpochSecond() - deUsuario.peekFirst().getEpochSecond());
                throw new LimiteDeConsultasException(Math.max(1, espera));
            }
            deUsuario.addLast(ahora);
        }
    }
}
