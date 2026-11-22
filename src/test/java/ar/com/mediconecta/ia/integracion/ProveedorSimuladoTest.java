package ar.com.mediconecta.ia.integracion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.com.mediconecta.ia.business.ProveedorIAException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProveedorSimuladoTest {

    private final ProveedorSimulado simulado = new ProveedorSimulado();

    @AfterEach
    void limpiar() {
        System.clearProperty("mediconecta.ia.simularFalla");
    }

    @Test
    void respondeEnElFormatoQueEsperaElGateway() {
        assertEquals("ESPECIALIDAD: Dermatología\nMOTIVO: Lo que describís suele atenderlo Dermatología.",
                simulado.responder("x", "<consulta>\nme pica la piel\n</consulta>"));
        assertEquals("simulado", simulado.nombre());
    }

    @Test
    void buscaPalabrasCompletas() {
        assertTrue(ProveedorSimulado.contiene("tengo el ojo hinchado", "ojo"));
        assertFalse(ProveedorSimulado.contiene("tengo la remera roja y un rojo", "ojo"));
        assertFalse(ProveedorSimulado.contiene("una tostada", "tos"));
        assertTrue(ProveedorSimulado.contiene("mucha tos.", "tos"));
    }

    @Test
    void conFallaCaidoLanzaUnErrorDelProveedor() {
        System.setProperty("mediconecta.ia.simularFalla", "caido");
        assertThrows(ProveedorIAException.class, () -> simulado.responder("x", "me pica la piel"));
    }

    @Test
    void conFallaLentoEsperaHastaQueLoCortan() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread hilo = new Thread(() -> {
            try {
                ProveedorSimulado.simularFalla("lento");
            } catch (Throwable t) {
                error.set(t);
            }
        });
        hilo.start();
        Thread.sleep(100);
        hilo.interrupt();
        hilo.join(2_000);
        assertFalse(hilo.isAlive());
        assertTrue(error.get() instanceof ProveedorIAException);
    }

    @Test
    void sinFallaConfiguradaNoHaceNada() {
        assertDoesNotThrow(() -> ProveedorSimulado.simularFalla(null));
        assertDoesNotThrow(() -> ProveedorSimulado.simularFalla("otra"));
    }
}
