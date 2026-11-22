package ar.com.mediconecta.ia.business;

import ar.com.mediconecta.config.Config;
import ar.com.mediconecta.ia.integracion.ProveedorClaude;
import ar.com.mediconecta.ia.integracion.ProveedorSimulado;
import ar.com.mediconecta.seguridad.Roles;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.Singleton;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

/**
 * Orientador de especialidad: el paciente cuenta que le pasa y la app le sugiere con que
 * especialidad sacar turno. Todo pasa por GatewayIA (validaciones, limite, 5 s, respaldo).
 *
 * Es @Singleton para que haya UN gateway y UN limite de consultas compartido por todos los
 * pedidos; el gateway es seguro para varios hilos, asi que la concurrencia la maneja el bean.
 * No toca la base: no abre transaccion.
 *
 * Configuracion (system property de WildFly o variable de entorno):
 *   mediconecta.ia.proveedor / MEDICONECTA_IA_PROVEEDOR         simulado (defecto) | claude
 *   mediconecta.ia.apiKey / ANTHROPIC_API_KEY                   solo para claude
 *   mediconecta.ia.modelo / MEDICONECTA_IA_MODELO               defecto claude-opus-5-5
 *   mediconecta.ia.consultasPorMinuto / MEDICONECTA_IA_CONSULTAS_POR_MINUTO   defecto 5
 */
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
@PermitAll
public class OrientadorService {

    private static final Logger LOG = Logger.getLogger(OrientadorService.class.getName());

    static final Duration ESPERA = Duration.ofSeconds(5);
    static final int CONSULTAS_POR_MINUTO = 5;

    /** Hilos administrados por WildFly (no se crean hilos propios dentro del servidor). */
    @Resource(lookup = "java:comp/DefaultManagedExecutorService")
    private ExecutorService ejecutor;

    private GatewayIA gateway;

    @PostConstruct
    void iniciar() {
        ProveedorIA proveedor = crearProveedor(
                Config.valor("mediconecta.ia.proveedor", "MEDICONECTA_IA_PROVEEDOR"),
                Config.valor("mediconecta.ia.apiKey", "ANTHROPIC_API_KEY"),
                Config.valor("mediconecta.ia.modelo", "MEDICONECTA_IA_MODELO"));
        int maximo = consultasPorMinuto(Config.valor("mediconecta.ia.consultasPorMinuto", "MEDICONECTA_IA_CONSULTAS_POR_MINUTO"));
        gateway = new GatewayIA(proveedor, new LimiteDeConsultas(maximo, Clock.systemUTC()), ejecutor, ESPERA);
        LOG.info("[IA] orientador listo con el proveedor " + proveedor.nombre() + " (" + maximo + " consultas por minuto)");
    }

    @RolesAllowed(Roles.PACIENTE)
    public Orientacion orientar(Long pacienteId, String sintomas) {
        return gateway.orientar(pacienteId, sintomas);
    }

    /** Sin configuracion, o claude sin API key, se usa el simulado: la app nunca queda sin orientador. */
    static ProveedorIA crearProveedor(String nombre, String apiKey, String modelo) {
        if (nombre == null || nombre.equalsIgnoreCase("simulado")) {
            return new ProveedorSimulado();
        }
        if (!nombre.equalsIgnoreCase("claude")) {
            throw new IllegalStateException("Proveedor de IA desconocido: " + nombre + " (usá simulado o claude)");
        }
        if (apiKey == null) {
            LOG.warning("[IA] mediconecta.ia.proveedor=claude pero falta ANTHROPIC_API_KEY: se usa el simulado");
            return new ProveedorSimulado();
        }
        return new ProveedorClaude(apiKey, modelo != null ? modelo : ProveedorClaude.MODELO_POR_DEFECTO, ESPERA);
    }

    static int consultasPorMinuto(String valor) {
        if (valor == null) {
            return CONSULTAS_POR_MINUTO;
        }
        try {
            return Math.max(1, Integer.parseInt(valor.strip()));
        } catch (NumberFormatException e) {
            LOG.warning("[IA] mediconecta.ia.consultasPorMinuto no es un número: se usa " + CONSULTAS_POR_MINUTO);
            return CONSULTAS_POR_MINUTO;
        }
    }
}
