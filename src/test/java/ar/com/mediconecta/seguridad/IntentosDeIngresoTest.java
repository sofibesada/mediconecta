package ar.com.mediconecta.seguridad;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntentosDeIngresoTest {

    /** Reloj que se puede adelantar a mano. */
    static class RelojDePrueba extends Clock {
        private Instant ahora = Instant.parse("2026-11-26T12:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private RelojDePrueba reloj;
    private IntentosDeIngreso intentos;

    @BeforeEach
    void preparar() {
        reloj = new RelojDePrueba();
        intentos = new IntentosDeIngreso(reloj);
    }

    @AfterEach
    void limpiar() {
        System.clearProperty("mediconecta.login.maxFallidos");
        System.clearProperty("mediconecta.login.ventanaSegundos");
    }

    private void fallar(String email, int veces) {
        for (int i = 0; i < veces; i++) {
            intentos.verificar(email);
            intentos.registrarFallido(email);
            reloj.avanzar(Duration.ofSeconds(10));
        }
    }

    @Test
    void conCuatroFallidosTodaviaSePuedeIntentarYConCincoSeFrena() {
        fallar("ana@x.com", 4);
        assertDoesNotThrow(() -> intentos.verificar("ana@x.com"));

        fallar("ana@x.com", 1);
        IngresoBloqueadoException ex = assertThrows(IngresoBloqueadoException.class, () -> intentos.verificar("ana@x.com"));
        // El primer fallido fue hace 50 s: faltan 850 s para que salga de la ventana de 15 minutos.
        assertEquals(850, ex.getSegundosParaReintentar());
    }

    @Test
    void cuandoElFallidoMasViejoSaleDeLaVentanaSePuedeVolverAIntentar() {
        fallar("ana@x.com", 5);
        reloj.avanzar(Duration.ofSeconds(850));
        assertDoesNotThrow(() -> intentos.verificar("ana@x.com"));
    }

    @Test
    void unIngresoCorrectoLimpiaElContador() {
        fallar("ana@x.com", 4);
        intentos.limpiar("ana@x.com");
        fallar("ana@x.com", 4);
        assertDoesNotThrow(() -> intentos.verificar("ana@x.com"));
    }

    @Test
    void laCuentaSeReconoceSinImportarMayusculasNiEspaciosYNoAfectaAOtras() {
        fallar(" Ana@X.com ", 5);
        assertThrows(IngresoBloqueadoException.class, () -> intentos.verificar("ana@x.com"));
        assertDoesNotThrow(() -> intentos.verificar("otra@x.com"));
        // Un email inexistente o vacío se cuenta igual: no revela qué cuentas existen.
        fallar(null, 5);
        assertThrows(IngresoBloqueadoException.class, () -> intentos.verificar(""));
    }

    @Test
    void elMaximoYLaVentanaSeConfiguran() {
        System.setProperty("mediconecta.login.maxFallidos", "2");
        System.setProperty("mediconecta.login.ventanaSegundos", "60");
        fallar("ana@x.com", 2);
        IngresoBloqueadoException ex = assertThrows(IngresoBloqueadoException.class, () -> intentos.verificar("ana@x.com"));
        assertEquals(40, ex.getSegundosParaReintentar());

        System.setProperty("mediconecta.login.maxFallidos", "cero");
        assertEquals(IntentosDeIngreso.MAX_POR_DEFECTO, IntentosDeIngreso.maximo(), "un valor inválido usa el de defecto");
    }

    @Test
    void elMensajeDiceCuantoEsperarEnMinutosOSegundos() {
        assertEquals("1 segundo", IngresoBloqueadoException.minutosOSegundos(1));
        assertEquals("45 segundos", IngresoBloqueadoException.minutosOSegundos(45));
        assertEquals("1 minuto", IngresoBloqueadoException.minutosOSegundos(60));
        assertEquals("15 minutos", IngresoBloqueadoException.minutosOSegundos(850));
    }
}
