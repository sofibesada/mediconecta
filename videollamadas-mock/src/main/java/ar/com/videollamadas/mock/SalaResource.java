package ar.com.videollamadas.mock;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * API del proveedor simulado de videollamadas.
 *
 *   POST   /rooms        crea una sala (exige Authorization e Idempotency-Key)
 *   GET    /rooms/{id}   consulta una sala
 *   DELETE /rooms/{id}   elimina una sala
 *
 * Como Zoom, cada sala tiene dos links: joinUrl (invitados) y hostUrl (anfitrion).
 * Los dos son links reales de Jitsi Meet, asi la videollamada funciona.
 *
 * Para simular fallas del proveedor (system properties de WildFly):
 *   videollamadas.mock.fallar=true    responde 503 en POST/DELETE
 *   videollamadas.mock.fallarPedidos=2  responde 503 en los proximos 2 POST/DELETE y despues anda
 *   videollamadas.mock.demoraMs=7000  tarda N ms en responder (provoca timeout del cliente)
 */
@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
public class SalaResource {

    private static final Logger LOG = Logger.getLogger(SalaResource.class.getName());

    @Inject
    private SalasEnMemoria salas;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response crear(@HeaderParam("Authorization") String auth,
                          @HeaderParam("Idempotency-Key") String idempotencyKey,
                          CrearSalaRequest request) {
        Response error = validarAutorizacion(auth);
        if (error != null) return error;
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return problema(400, "idempotency-key-requerida", "Falta el header Idempotency-Key");
        }
        if (request == null || request.referencia == null || request.referencia.isBlank()) {
            return problema(400, "datos-invalidos", "El campo referencia es obligatorio");
        }
        error = simularFalla();
        if (error != null) return error;

        Sala existente = salas.buscarPorClave(idempotencyKey);
        if (existente != null) {
            LOG.info("[Mock videollamadas] POST /rooms repetido (Idempotency-Key " + idempotencyKey + ") -> misma sala " + existente.id);
            return Response.ok(existente).build();
        }

        Sala sala = new Sala();
        sala.id = "room_" + UUID.randomUUID().toString().substring(0, 8);
        sala.referencia = request.referencia;
        sala.inicio = request.inicio;
        sala.duracionMinutos = request.duracionMinutos != null ? request.duracionMinutos : 30;
        sala.creadaEn = LocalDateTime.now().toString();
        sala.joinUrl = "https://meet.jit.si/MediConecta-" + request.referencia.replaceAll("[^A-Za-z0-9-]", "") + "-" + sala.id.substring(5);
        // Jitsi lee los parametros del # como JSON e ignora los que no conoce.
        sala.hostUrl = sala.joinUrl + "#rol=%22anfitrion%22";
        salas.guardar(idempotencyKey, sala);
        LOG.info("[Mock videollamadas] sala creada " + sala.id + " para " + sala.referencia + " -> " + sala.joinUrl);
        return Response.created(URI.create("rooms/" + sala.id)).entity(sala).build();
    }

    @GET
    @Path("/{id}")
    public Response consultar(@HeaderParam("Authorization") String auth, @PathParam("id") String id) {
        Response error = validarAutorizacion(auth);
        if (error != null) return error;
        Sala sala = salas.buscar(id);
        return sala != null ? Response.ok(sala).build() : problema(404, "sala-inexistente", "No existe la sala " + id);
    }

    @DELETE
    @Path("/{id}")
    public Response eliminar(@HeaderParam("Authorization") String auth, @PathParam("id") String id) {
        Response error = validarAutorizacion(auth);
        if (error != null) return error;
        error = simularFalla();
        if (error != null) return error;
        if (!salas.eliminar(id)) {
            return problema(404, "sala-inexistente", "No existe la sala " + id);
        }
        LOG.info("[Mock videollamadas] sala eliminada " + id);
        return Response.noContent().build();
    }

    private Response validarAutorizacion(String auth) {
        // La API key se configura afuera (system property o variable de entorno VIDEOLLAMADAS_API_KEY).
        String clave = System.getProperty("videollamadas.mock.apiKey", System.getenv("VIDEOLLAMADAS_API_KEY"));
        return clave != null && ("Bearer " + clave).equals(auth) ? null : problema(401, "no-autorizado", "API key inválida o ausente");
    }

    /** Valor de fallarPedidos que se esta contando y cuantos pedidos ya fallaron con el. */
    private static final AtomicReference<String> FALLAR_PEDIDOS_ACTUAL = new AtomicReference<>();
    private static final AtomicInteger PEDIDOS_FALLADOS = new AtomicInteger();

    /**
     * videollamadas.mock.fallarPedidos=N: fallan los proximos N pedidos y despues responde bien.
     * Sirve para mostrar que los reintentos de la cola alcanzan cuando el proveedor se recupera.
     */
    private static boolean fallaPorCantidad() {
        String valor = System.getProperty("videollamadas.mock.fallarPedidos");
        if (valor == null) {
            FALLAR_PEDIDOS_ACTUAL.set(null);
            return false;
        }
        if (!valor.equals(FALLAR_PEDIDOS_ACTUAL.getAndSet(valor))) {
            PEDIDOS_FALLADOS.set(0); // valor nuevo: se empieza a contar de cero
        }
        int n;
        try {
            n = Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        return PEDIDOS_FALLADOS.incrementAndGet() <= n;
    }

    private Response simularFalla() {
        long demora = Long.getLong("videollamadas.mock.demoraMs", 0L);
        if (demora > 0) {
            try { Thread.sleep(demora); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (Boolean.getBoolean("videollamadas.mock.fallar") || fallaPorCantidad()) {
            LOG.warning("[Mock videollamadas] falla simulada -> 503");
            return problema(503, "servicio-no-disponible", "Falla simulada del proveedor de videollamadas");
        }
        return null;
    }

    /** Errores en formato Problem Details (RFC 9457). */
    private Response problema(int status, String tipo, String detalle) {
        return Response.status(status)
                .type("application/problem+json")
                .entity(Map.of(
                        "type", "https://videollamadas.example/problemas/" + tipo,
                        "title", tipo.replace('-', ' '),
                        "status", status,
                        "detail", detalle))
                .build();
    }
}
