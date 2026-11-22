package ar.com.mediconecta.ia.integracion;

import ar.com.mediconecta.config.Config;
import ar.com.mediconecta.ia.business.ProveedorIA;
import ar.com.mediconecta.ia.business.ProveedorIAException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Proveedor por defecto: responde con palabras clave, sin red ni costo. Sirve para correr
 * la app y las pruebas sin una API key, y para la demo de fallas:
 *
 *   mediconecta.ia.simularFalla = lento   tarda 8 s (el gateway corta a los 5 s)
 *                               = caido   lanza un error, como si el servicio no respondiera
 *
 * Se lee en cada consulta, asi se puede activar en vivo con jboss-cli.
 */
public class ProveedorSimulado implements ProveedorIA {

    static final long DEMORA_LENTO_MS = 8_000;

    /** En orden: la primera que coincide gana (por eso Pediatria va primero). Palabras completas. */
    private static final List<Map.Entry<String, List<String>>> REGLAS = List.of(
            Map.entry("Pediatría", List.of("mi hijo", "mi hija", "mi bebe", "mi nene", "mi nena", "el nene", "la nena")),
            Map.entry("Dermatología", List.of("piel", "pica", "picazon", "granito", "granitos", "acne", "mancha", "manchas", "lunar", "lunares", "sarpullido", "roncha", "ronchas", "eccema", "caspa")),
            Map.entry("Oftalmología", List.of("ojo", "ojos", "vista", "veo borroso", "vision")),
            Map.entry("Otorrinolaringología", List.of("oido", "oidos", "garganta", "nariz", "sinusitis", "ronquera", "zumbido")),
            Map.entry("Cardiología", List.of("palpitacion", "palpitaciones", "presion alta", "hipertension", "taquicardia", "corazon")),
            Map.entry("Gastroenterología", List.of("estomago", "panza", "abdomen", "diarrea", "acidez", "vomito", "vomitos", "vomitar", "nausea", "nauseas", "reflujo", "digestion")),
            Map.entry("Traumatología", List.of("rodilla", "rodillas", "hueso", "huesos", "fractura", "esguince", "tobillo", "espalda", "columna", "hombro", "codo", "muneca")),
            Map.entry("Neurología", List.of("migrana", "dolor de cabeza", "me duele la cabeza", "hormigueo", "temblor")),
            Map.entry("Ginecología", List.of("menstruacion", "embarazo", "ovario", "periodo", "flujo vaginal")),
            Map.entry("Urología", List.of("orinar", "orina", "prostata", "rinon")),
            Map.entry("Endocrinología", List.of("tiroides", "diabetes", "azucar en sangre", "hormona", "hormonas")),
            Map.entry("Nutrición", List.of("bajar de peso", "dieta", "alimentacion", "sobrepeso")),
            Map.entry("Psicología", List.of("ansiedad", "angustia", "estres", "triste", "insomnio", "no puedo dormir")),
            Map.entry("Kinesiología", List.of("rehabilitacion", "contractura")),
            Map.entry("Clínica médica", List.of("fiebre", "tos", "gripe", "resfrio", "cansancio", "malestar", "me siento mal")));

    /** Si no hay una regla pero se habla de sintomas, se sugiere Clinica medica. */
    private static final List<String> SINTOMAS = List.of("duele", "duelen", "dolor", "dolores", "siento", "sintoma", "sintomas", "molestia", "molestias", "tengo");

    @Override
    public String nombre() {
        return "simulado";
    }

    @Override
    public String responder(String instrucciones, String consulta) {
        simularFalla(Config.valor("mediconecta.ia.simularFalla", "MEDICONECTA_IA_SIMULAR_FALLA"));
        String texto = normalizar(consulta);
        for (Map.Entry<String, List<String>> regla : REGLAS) {
            if (regla.getValue().stream().anyMatch(p -> contiene(texto, p))) {
                return respuesta(regla.getKey());
            }
        }
        if (SINTOMAS.stream().anyMatch(p -> contiene(texto, p))) {
            return respuesta("Clínica médica");
        }
        return "ESPECIALIDAD: NINGUNA\nMOTIVO: La consulta no describe síntomas.";
    }

    static void simularFalla(String modo) {
        if ("caido".equalsIgnoreCase(modo)) {
            throw new ProveedorIAException("simulado: servicio no disponible");
        }
        if ("lento".equalsIgnoreCase(modo)) {
            try {
                Thread.sleep(DEMORA_LENTO_MS);
            } catch (InterruptedException e) {
                // El gateway corto la espera: se abandona la consulta.
                Thread.currentThread().interrupt();
                throw new ProveedorIAException("simulado: consulta cancelada");
            }
        }
    }

    private static String respuesta(String especialidad) {
        return "ESPECIALIDAD: " + especialidad + "\nMOTIVO: Lo que describís suele atenderlo " + especialidad + ".";
    }

    /** Palabra o frase completa: "rojo" no cuenta como "ojo" ni "tostada" como "tos". */
    static boolean contiene(String texto, String frase) {
        return Pattern.compile("(?<![a-z])" + Pattern.quote(frase) + "(?![a-z])").matcher(texto).find();
    }

    private static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }
}
