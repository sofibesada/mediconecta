package ar.com.pagos.mock;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Pasarela de pagos simulada (proveedor EXTERNO, REST).
 *
 *   POST /pagos       cobra con tarjeta (exige Authorization: Bearer <api key> e Idempotency-Key)
 *   GET  /pagos/{id}  consulta un pago
 *
 * Tarjetas de prueba (numeros con digito verificador valido, como los reales):
 *   4000 0000 0000 0002  rechazada por el banco emisor
 *   4000 0000 0000 9995  fondos insuficientes
 *   cualquier otra tarjeta valida (marca, largo, Luhn, vencimiento, CVV) se aprueba
 *
 * Nunca guarda el numero completo ni el codigo de seguridad: solo la marca y los ultimos 4.
 *
 * Fallas simuladas (system properties de WildFly):
 *   pagos.mock.fallar=true    responde 503
 *   pagos.mock.demoraMs=7000  tarda N ms (provoca el timeout del cliente)
 */
@Path("/pagos")
@Produces(MediaType.APPLICATION_JSON)
public class PagoResource {

    private static final Logger LOG = Logger.getLogger(PagoResource.class.getName());
    private static final Map<String, Pago> PAGOS = new ConcurrentHashMap<>();
    private static final Map<String, String> POR_CLAVE = new ConcurrentHashMap<>();

    public static class DatosTarjeta {
        public String numero;
        public String vencimiento;
        public String cvv;
        public String titular;
    }

    public static class PedidoDePago {
        public BigDecimal monto;
        public String moneda;
        public String referencia;
        public DatosTarjeta tarjeta;
    }

    public static class Pago {
        public String id;
        public String estado; // APROBADO o RECHAZADO
        public String motivo;
        public String marca;
        public String ultimos4;
        public String codigoAutorizacion;
        public BigDecimal monto;
        public String moneda;
        public String referencia;
        public String creadoEn;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response cobrar(@HeaderParam("Authorization") String auth, @HeaderParam("Idempotency-Key") String clave,
                           PedidoDePago pedido) {
        Response error = controlar(auth);
        if (error != null) {
            return error;
        }
        if (clave == null || clave.isBlank()) {
            return problema(400, "falta-idempotency-key", "Falta el encabezado Idempotency-Key");
        }
        // Mismo pedido repetido (reintento): la misma respuesta, sin cobrar dos veces.
        String previo = POR_CLAVE.get(clave);
        if (previo != null) {
            return Response.ok(PAGOS.get(previo)).header("Idempotent-Replayed", "true").build();
        }
        if (pedido == null || pedido.tarjeta == null || pedido.monto == null || pedido.monto.signum() <= 0) {
            return problema(400, "pedido-invalido", "Faltan el monto o los datos de la tarjeta");
        }
        DatosTarjeta t = pedido.tarjeta;
        String numero = Tarjeta.soloDigitos(t.numero);
        String marca = Tarjeta.marca(numero);
        if (marca == null || !Tarjeta.largoValido(marca, numero.length()) || !Tarjeta.luhn(numero)) {
            return problema(422, "tarjeta-invalida", "El número de tarjeta no es válido");
        }
        if (!Tarjeta.vigente(t.vencimiento, YearMonth.now())) {
            return problema(422, "tarjeta-vencida", "La tarjeta está vencida o la fecha no es válida (MM/AA)");
        }
        if (!Tarjeta.cvvValido(marca, t.cvv)) {
            return problema(422, "cvv-invalido", "American Express".equals(marca)
                    ? "El código de seguridad de American Express tiene 4 dígitos"
                    : "El código de seguridad tiene 3 dígitos");
        }
        if (t.titular == null || t.titular.isBlank()) {
            return problema(422, "titular", "Falta el nombre del titular como figura en la tarjeta");
        }

        Pago p = new Pago();
        p.id = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        p.marca = marca;
        p.ultimos4 = numero.substring(numero.length() - 4);
        p.monto = pedido.monto;
        p.moneda = pedido.moneda == null ? "ARS" : pedido.moneda;
        p.referencia = pedido.referencia;
        p.creadoEn = LocalDateTime.now().toString();
        switch (numero) {
            case "4000000000000002" -> rechazar(p, "La tarjeta fue rechazada por el banco emisor");
            case "4000000000009995" -> rechazar(p, "Fondos insuficientes");
            default -> {
                p.estado = "APROBADO";
                p.codigoAutorizacion = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
            }
        }
        PAGOS.put(p.id, p);
        POR_CLAVE.put(clave, p.id);
        LOG.info("[Mock pagos] " + p.referencia + " " + p.marca + " ****" + p.ultimos4 + " $" + p.monto + " -> " + p.estado);
        return Response.status(201).entity(p).build();
    }

    @GET
    @Path("/{id}")
    public Response consultar(@HeaderParam("Authorization") String auth, @PathParam("id") String id) {
        Response error = controlar(auth);
        if (error != null) {
            return error;
        }
        Pago p = PAGOS.get(id);
        return p == null ? problema(404, "no-encontrado", "Pago inexistente") : Response.ok(p).build();
    }

    private static void rechazar(Pago p, String motivo) {
        p.estado = "RECHAZADO";
        p.motivo = motivo;
    }

    /** API key, falla y demora simuladas. */
    private static Response controlar(String auth) {
        String clave = System.getProperty("pagos.mock.apiKey", System.getenv("PAGOS_API_KEY"));
        if (clave == null || !("Bearer " + clave).equals(auth)) {
            return problema(401, "no-autorizado", "API key inválida o ausente");
        }
        if (Boolean.getBoolean("pagos.mock.fallar")) {
            return problema(503, "no-disponible", "Pasarela fuera de servicio (simulado)");
        }
        long demora = Long.getLong("pagos.mock.demoraMs", 0L);
        if (demora > 0) {
            try {
                Thread.sleep(demora);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    private static Response problema(int status, String tipo, String detalle) {
        return Response.status(status).type("application/problem+json")
                .entity(Map.of("type", "https://pagos.example/errores/" + tipo, "title", tipo, "status", status, "detail", detalle))
                .build();
    }
}
