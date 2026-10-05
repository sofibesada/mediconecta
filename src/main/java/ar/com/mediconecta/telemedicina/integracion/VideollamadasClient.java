package ar.com.mediconecta.telemedicina.integracion;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Cliente REST (JAX-RS Client) de la plataforma externa de videollamadas.
 * Integracion SINCRONICA: espera la respuesta, con timeouts para no colgar el hilo.
 *
 * Configuracion (system properties de WildFly, con valores por defecto para desarrollo):
 *   mediconecta.videollamadas.url     base de la API del proveedor
 *   mediconecta.videollamadas.apiKey  credencial que exige el proveedor
 */
@ApplicationScoped
public class VideollamadasClient {

    private static final Logger LOG = Logger.getLogger(VideollamadasClient.class.getName());
    private static final String URL_POR_DEFECTO = "http://localhost:8080/videollamadas-mock/api/v1";
    private static final String API_KEY_POR_DEFECTO = "mediconecta-dev-key";

    private Client client;

    @PostConstruct
    void init() {
        client = ClientBuilder.newBuilder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    @PreDestroy
    void cerrar() {
        client.close();
    }

    /**
     * Crea la sala. idempotencyKey = eventoId del mensaje JMS: si el mensaje se
     * reentrega y la sala ya se habia creado, el proveedor devuelve la misma.
     */
    public SalaExterna crearSala(Long turnoId, LocalDateTime inicio, String idempotencyKey) {
        Map<String, Object> body = Map.of(
                "referencia", "turno-" + turnoId,
                "inicio", inicio.toString(),
                "duracionMinutos", 30);
        try (Response r = client.target(baseUrl()).path("rooms")
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey())
                .header("Idempotency-Key", idempotencyKey)
                .post(Entity.json(body))) {
            if (r.getStatus() != 200 && r.getStatus() != 201) {
                throw new VideollamadasNoDisponibleException(
                        "La plataforma de videollamadas respondió " + r.getStatus() + ": " + r.readEntity(String.class), null);
            }
            SalaExterna sala = r.readEntity(SalaExterna.class);
            LOG.info("[REST externo] POST /rooms -> " + r.getStatus() + " sala " + sala.id + " (turno #" + turnoId + ")");
            return sala;
        } catch (ProcessingException e) {
            throw new VideollamadasNoDisponibleException("No se pudo contactar a la plataforma de videollamadas: " + e.getMessage(), e);
        }
    }

    /** Elimina la sala. 404 se acepta: si ya no existe, el resultado es el mismo (idempotente). */
    public void eliminarSala(String salaExternaId) {
        try (Response r = client.target(baseUrl()).path("rooms").path(salaExternaId)
                .request()
                .header("Authorization", "Bearer " + apiKey())
                .delete()) {
            if (r.getStatus() != 204 && r.getStatus() != 404) {
                throw new VideollamadasNoDisponibleException("La plataforma de videollamadas respondió " + r.getStatus(), null);
            }
            LOG.info("[REST externo] DELETE /rooms/" + salaExternaId + " -> " + r.getStatus());
        } catch (ProcessingException e) {
            throw new VideollamadasNoDisponibleException("No se pudo contactar a la plataforma de videollamadas: " + e.getMessage(), e);
        }
    }

    private String baseUrl() {
        return System.getProperty("mediconecta.videollamadas.url", URL_POR_DEFECTO);
    }

    private String apiKey() {
        return System.getProperty("mediconecta.videollamadas.apiKey", API_KEY_POR_DEFECTO);
    }
}
