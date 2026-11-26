package ar.com.mediconecta.ia.integracion;

import ar.com.mediconecta.ia.business.ProveedorIA;
import ar.com.mediconecta.ia.business.ProveedorIAException;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Claude (Anthropic) con el SDK oficial anthropic-java. Solo se crea si
 * mediconecta.ia.proveedor=claude y hay una ANTHROPIC_API_KEY (nunca va en el repositorio).
 *
 * - Sin reintentos y con el mismo limite de tiempo que el gateway: si no contesta a tiempo,
 *   el paciente ve el respaldo en lugar de esperar.
 * - Esfuerzo bajo: es una clasificacion corta, no hace falta razonar mucho (menos costo y demora).
 * - Si el modelo rechaza la consulta (stop reason "refusal") se trata como falla: respaldo.
 */
public class ProveedorClaude implements ProveedorIA {

    public static final String MODELO_POR_DEFECTO = "claude-opus-5-5";
    private static final long MAX_TOKENS = 1024;

    private final AnthropicClient cliente;
    private final String modelo;

    public ProveedorClaude(String apiKey, String modelo, Duration espera) {
        this.cliente = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(espera)
                .maxRetries(0)
                .build();
        this.modelo = modelo;
    }

    @Override
    public String nombre() {
        return "claude (" + modelo + ")";
    }

    @Override
    public String responder(String instrucciones, String consulta) {
        MessageCreateParams pedido = MessageCreateParams.builder()
                .model(modelo)
                .maxTokens(MAX_TOKENS)
                .system(instrucciones)
                .addUserMessage(consulta)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .build();
        Message respuesta;
        try {
            respuesta = cliente.messages().create(pedido);
        } catch (RuntimeException e) {
            // Sin conexion, clave invalida, limite de la API, timeout: el gateway usa el respaldo.
            throw new ProveedorIAException("claude: " + e.getClass().getSimpleName(), e);
        }
        if (respuesta.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            throw new ProveedorIAException("claude: el modelo rechazó la consulta");
        }
        return respuesta.content().stream()
                .flatMap(bloque -> bloque.text().stream())
                .map(texto -> texto.text())
                .collect(Collectors.joining("\n"));
    }
}
