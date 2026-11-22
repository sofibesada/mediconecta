package ar.com.mediconecta.ia.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.com.mediconecta.ia.integracion.ProveedorClaude;
import ar.com.mediconecta.ia.integracion.ProveedorSimulado;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OrientadorServiceTest {

    private final ExecutorService ejecutor = Executors.newCachedThreadPool();

    @AfterEach
    void limpiar() {
        ejecutor.shutdownNow();
        System.clearProperty("mediconecta.ia.proveedor");
        System.clearProperty("mediconecta.ia.consultasPorMinuto");
    }

    @Test
    void sinConfiguracionUsaElSimulado() {
        assertInstanceOf(ProveedorSimulado.class, OrientadorService.crearProveedor(null, null, null));
        assertInstanceOf(ProveedorSimulado.class, OrientadorService.crearProveedor("SIMULADO", "clave", null));
    }

    @Test
    void claudeSinApiKeyCaeAlSimulado() {
        assertInstanceOf(ProveedorSimulado.class, OrientadorService.crearProveedor("claude", null, null));
    }

    @Test
    void claudeConApiKeyUsaElModeloConfiguradoOElDeDefecto() {
        // Crear el cliente no hace ningun pedido a la red.
        ProveedorIA porDefecto = OrientadorService.crearProveedor("claude", "sk-ant-prueba", null);
        assertInstanceOf(ProveedorClaude.class, porDefecto);
        assertEquals("claude (" + ProveedorClaude.MODELO_POR_DEFECTO + ")", porDefecto.nombre());
        assertEquals("claude (otro-modelo)", OrientadorService.crearProveedor("Claude", "sk-ant-prueba", "otro-modelo").nombre());
    }

    @Test
    void unProveedorDesconocidoEsUnErrorDeConfiguracion() {
        assertThrows(IllegalStateException.class, () -> OrientadorService.crearProveedor("gpt", "x", null));
    }

    @Test
    void consultasPorMinutoTomaElValorConfiguradoOElDeDefecto() {
        assertEquals(OrientadorService.CONSULTAS_POR_MINUTO, OrientadorService.consultasPorMinuto(null));
        assertEquals(OrientadorService.CONSULTAS_POR_MINUTO, OrientadorService.consultasPorMinuto("muchas"));
        assertEquals(1, OrientadorService.consultasPorMinuto("0"));
        assertEquals(12, OrientadorService.consultasPorMinuto(" 12 "));
    }

    @Test
    void alIniciarArmaElGatewayConLaConfiguracionYOrienta() throws Exception {
        System.setProperty("mediconecta.ia.proveedor", "simulado");
        System.setProperty("mediconecta.ia.consultasPorMinuto", "1");
        OrientadorService servicio = new OrientadorService();
        Field campo = OrientadorService.class.getDeclaredField("ejecutor");
        campo.setAccessible(true);
        campo.set(servicio, ejecutor);
        servicio.iniciar();

        Orientacion o = servicio.orientar(3L, "me pica la piel");
        assertEquals("Dermatología", o.especialidad());
        // Con el limite configurado en 1, la segunda consulta del minuto se rechaza.
        LimiteDeConsultasException ex = assertThrows(LimiteDeConsultasException.class, () -> servicio.orientar(3L, "me pica la piel"));
        assertTrue(ex.getMessage().contains("segundos"));
    }
}
