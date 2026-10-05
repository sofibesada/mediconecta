package ar.com.videollamadas.mock;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Almacen en memoria del simulador (se pierde al redesplegar, como corresponde a un mock). */
@ApplicationScoped
public class SalasEnMemoria {

    private final Map<String, Sala> porId = new ConcurrentHashMap<>();
    /** Idempotency-Key -> id de la sala creada con esa clave. */
    private final Map<String, String> porClave = new ConcurrentHashMap<>();

    public Sala buscarPorClave(String clave) {
        String id = porClave.get(clave);
        return id != null ? porId.get(id) : null;
    }

    public void guardar(String clave, Sala sala) {
        porId.put(sala.id, sala);
        porClave.put(clave, sala.id);
    }

    public Sala buscar(String id) {
        return porId.get(id);
    }

    public boolean eliminar(String id) {
        return porId.remove(id) != null;
    }
}
