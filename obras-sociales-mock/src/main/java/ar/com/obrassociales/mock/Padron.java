package ar.com.obrassociales.mock;

import ar.com.obrassociales.mock.Contrato.ObraSocial;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Padron ficticio del sistema legado: obras sociales, planes y reglas de afiliados.
 *
 * Reglas para poder demostrar cada caso (numero de afiliado de 8 digitos):
 *   - termina en 9: afiliado dado de baja (sin cobertura)
 *   - termina en 7: tiene cobertura, pero la prestacion no se autoriza
 *   - cualquier otro: cubierto y autorizado
 *
 * Las autorizaciones y los reclamos se guardan por "referencia" (la manda MediConecta):
 * si el mismo pedido llega dos veces, se devuelve el mismo resultado (idempotencia).
 */
final class Padron {

    record Plan(String codigoObraSocial, String nombre, String tipo, String plan, int copagoPorcentaje) {}

    private static final Map<String, Plan> PLANES = Map.of(
            "OSMED", new Plan("OSMED", "Obra Social Medica Nacional", "OBRA_SOCIAL", "OSMED 210", 30),
            "SALUDPLUS", new Plan("SALUDPLUS", "Salud Plus Prepaga", "PREPAGA", "Plus 410", 0),
            "PREVENIR", new Plan("PREVENIR", "Prevenir Salud", "OBRA_SOCIAL", "Prevenir Clasico", 20));

    static final Map<String, Contrato.ResultadoAutorizacion> AUTORIZACIONES = new ConcurrentHashMap<>();
    static final Map<String, Contrato.ResultadoReclamo> RECLAMOS = new ConcurrentHashMap<>();
    static final AtomicLong SECUENCIA = new AtomicLong(1000);

    private Padron() {}

    static List<ObraSocial> obrasSociales() {
        return PLANES.values().stream()
                .sorted((a, b) -> a.nombre().compareTo(b.nombre()))
                .map(p -> new ObraSocial(p.codigoObraSocial(), p.nombre(), p.tipo()))
                .toList();
    }

    static Optional<Plan> plan(String codigoObraSocial) {
        return Optional.ofNullable(codigoObraSocial == null ? null : PLANES.get(codigoObraSocial));
    }

    static boolean afiliadoValido(String numero) {
        return numero != null && numero.matches("\\d{8}");
    }

    static boolean dadoDeBaja(String numero) {
        return numero.endsWith("9");
    }

    static boolean prestacionNoCubierta(String numero) {
        return numero.endsWith("7");
    }
}
