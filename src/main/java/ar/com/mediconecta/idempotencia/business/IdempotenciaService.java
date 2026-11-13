package ar.com.mediconecta.idempotencia.business;

import ar.com.mediconecta.idempotencia.data.IdempotenciaRepository;
import ar.com.mediconecta.idempotencia.model.RegistroIdempotencia;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Idempotency-Key: el cliente manda una clave unica por operacion (un UUID). Si repite el
 * pedido con la misma clave, recibe la misma respuesta y la operacion NO se ejecuta de
 * nuevo. Evita, por ejemplo, confirmar dos veces un turno (y facturarlo dos veces) cuando
 * se corta la conexion y el cliente reintenta.
 *
 * La operacion y el registro van en la MISMA transaccion (REQUIRED): o quedan los dos o
 * ninguno. Solo se guardan las respuestas exitosas; si la operacion falla, el cliente puede
 * reintentar con la misma clave. Las claves valen 24 horas.
 */
@Stateless
public class IdempotenciaService {

    private static final Logger LOG = Logger.getLogger(IdempotenciaService.class.getName());
    private static final Jsonb JSONB = JsonbBuilder.create();
    static final int LARGO_MAXIMO = 100;
    static final long HORAS_DE_VALIDEZ = 24;

    @Inject
    private IdempotenciaRepository repository;

    /** Resultado: el cuerpo nuevo, o el JSON guardado si la clave ya se habia usado. */
    public record Resultado(Object cuerpo, String json, boolean repetido) {}

    /**
     * Ejecuta la accion una sola vez por clave. La accion y el registro van en esta misma
     * transaccion: si la accion falla, no queda registro y se puede reintentar.
     */
    public Resultado ejecutar(String clave, Long usuarioId, String operacion, Supplier<?> accion) {
        if (clave == null || clave.isBlank()) {
            return new Resultado(accion.get(), null, false);
        }
        String previo = respuestaPrevia(clave, usuarioId, operacion);
        if (previo != null) {
            return new Resultado(null, previo, true);
        }
        Object cuerpo = accion.get();
        registrar(clave, usuarioId, operacion, cuerpo);
        return new Resultado(cuerpo, null, false);
    }

    /**
     * Respuesta ya guardada para esta clave, o null si es nueva. Para operaciones que no
     * pueden ir dentro de otra transaccion (por ejemplo, el @Remove de un bean @Stateful):
     * se consulta antes, se ejecuta la operacion y despues se llama a registrar().
     */
    public String respuestaPrevia(String clave, Long usuarioId, String operacion) {
        if (clave == null || clave.isBlank()) {
            return null;
        }
        RegistroIdempotencia previo = repository.buscar(validar(clave), usuarioId, LocalDateTime.now().minusHours(HORAS_DE_VALIDEZ));
        if (previo == null) {
            return null;
        }
        if (!previo.getOperacion().equals(operacion)) {
            throw new ClaveReutilizadaException();
        }
        LOG.info("[Idempotencia] clave repetida para " + operacion + ": se devuelve la respuesta guardada");
        return previo.getRespuestaJson();
    }

    /** Guarda la respuesta exitosa de una operacion con su clave. */
    public void registrar(String clave, Long usuarioId, String operacion, Object cuerpo) {
        if (clave == null || clave.isBlank()) {
            return;
        }
        RegistroIdempotencia nuevo = new RegistroIdempotencia();
        nuevo.setClave(validar(clave));
        nuevo.setUsuarioId(usuarioId);
        nuevo.setOperacion(operacion);
        nuevo.setRespuestaJson(JSONB.toJson(cuerpo));
        nuevo.setCreadoEn(LocalDateTime.now());
        repository.guardar(nuevo);
    }

    private static String validar(String clave) {
        String c = clave.trim();
        if (c.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("La Idempotency-Key no puede tener más de " + LARGO_MAXIMO + " caracteres");
        }
        return c;
    }
}
