package ar.com.mediconecta.pagos.integracion;

import ar.com.mediconecta.config.Config;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.JsonObject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Cliente REST (JAX-RS Client) de la pasarela de pagos externa. Integracion SINCRONICA: el
 * paciente espera el resultado del cobro. Timeouts de 2 s (conexion) y 5 s (respuesta).
 *
 * Los datos de la tarjeta solo pasan por aca hacia la pasarela: no se guardan ni se loguean.
 *
 * Configuracion: mediconecta.pagos.url (por defecto, el simulador local) y la API key en
 * mediconecta.pagos.apiKey o la variable de entorno PAGOS_API_KEY.
 */
@ApplicationScoped
public class PasarelaPagosClient {

    private static final Logger LOG = Logger.getLogger(PasarelaPagosClient.class.getName());
    private static final String URL_POR_DEFECTO = "http://localhost:8080/pagos-mock/api/v1";

    /** Respuesta de la pasarela: APROBADO o RECHAZADO (con su motivo). */
    public record Resultado(String id, String estado, String motivo, String marca, String ultimos4, String codigoAutorizacion) {
        public boolean aprobado() {
            return "APROBADO".equals(estado);
        }
    }

    /** La pasarela rechazo los datos de la tarjeta (422): se le muestra el motivo al paciente. */
    public static class DatosInvalidosException extends IllegalArgumentException {
        public DatosInvalidosException(String mensaje) {
            super(mensaje);
        }
    }

    private Client client;

    @PostConstruct
    void init() {
        client = ClientBuilder.newBuilder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build();
    }

    @PreDestroy
    void cerrar() {
        client.close();
    }

    public Resultado cobrar(BigDecimal monto, String referencia, String numero, String vencimiento, String cvv,
                            String titular, String claveIdempotencia) {
        Map<String, Object> cuerpo = Map.of(
                "monto", monto,
                "moneda", "ARS",
                "referencia", referencia,
                "tarjeta", Map.of("numero", numero, "vencimiento", vencimiento, "cvv", cvv, "titular", titular));
        try (Response r = client.target(baseUrl()).path("pagos")
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey())
                .header("Idempotency-Key", claveIdempotencia)
                .post(Entity.json(cuerpo))) {
            if (r.getStatus() == 422) {
                JsonObject problema = r.readEntity(JsonObject.class);
                throw new DatosInvalidosException(problema.getString("detail", "Los datos de la tarjeta no son válidos"));
            }
            if (r.getStatus() != 200 && r.getStatus() != 201) {
                throw new PasarelaNoDisponibleException("La pasarela de pagos respondió " + r.getStatus(), null);
            }
            JsonObject p = r.readEntity(JsonObject.class);
            Resultado resultado = new Resultado(p.getString("id"), p.getString("estado"), p.getString("motivo", null),
                    p.getString("marca", null), p.getString("ultimos4", null), p.getString("codigoAutorizacion", null));
            LOG.info("[REST externo] POST /pagos " + referencia + " -> " + resultado.estado());
            return resultado;
        } catch (ProcessingException e) {
            throw new PasarelaNoDisponibleException("La pasarela de pagos no responde. Probá de nuevo en unos minutos o pagá en el consultorio.", e);
        }
    }

    private String baseUrl() {
        return System.getProperty("mediconecta.pagos.url", URL_POR_DEFECTO);
    }

    private String apiKey() {
        String clave = Config.valor("mediconecta.pagos.apiKey", "PAGOS_API_KEY");
        if (clave == null) {
            throw new PasarelaNoDisponibleException("Falta configurar la API key de la pasarela de pagos", null);
        }
        return clave;
    }
}
