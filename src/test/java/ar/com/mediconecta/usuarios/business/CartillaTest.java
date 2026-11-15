package ar.com.mediconecta.usuarios.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CartillaTest {

    @Test
    void sinCartillaAtiendeTodas() {
        assertTrue(Cartilla.atiende(null, "OSDE"));
        assertNull(Cartilla.lista(null));
        assertNull(Cartilla.guardar(null));
    }

    @Test
    void conCartillaAtiendeSoloLasDeLaLista() {
        assertTrue(Cartilla.atiende("OSDE,SWISS", "osde"));
        assertTrue(Cartilla.atiende("OSDE,SWISS", " SWISS "));
        assertFalse(Cartilla.atiende("OSDE,SWISS", "PAMI"));
        assertFalse(Cartilla.atiende("OSDE", null));
        assertFalse(Cartilla.atiende("OSDE", " "));
        assertEquals(List.of("OSDE", "SWISS"), Cartilla.lista("OSDE, SWISS,"));
    }

    @Test
    void cartillaVaciaEsSoloParticular() {
        assertFalse(Cartilla.atiende("", "OSDE"));
        assertEquals(List.of(), Cartilla.lista(""));
        assertEquals("", Cartilla.guardar(List.of()));
    }

    @Test
    void guardaNormalizadoOrdenadoYSinRepetidos() {
        assertEquals("OSDE,PAMI,SWISS", Cartilla.guardar(List.of("swiss", " OSDE", "pami", "OSDE")));
    }

    @Test
    void rechazaCodigosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> Cartilla.guardar(List.of("OSDE; DROP")));
        assertThrows(IllegalArgumentException.class, () -> Cartilla.guardar(List.of("X")));
        assertThrows(IllegalArgumentException.class, () -> Cartilla.guardar(Arrays.asList("OSDE", null)));
    }

    @Test
    void noAceptaUnaCartillaGigante() {
        List<String> muchas = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            muchas.add("OBRA_SOCIAL_" + i);
        }
        assertThrows(IllegalArgumentException.class, () -> Cartilla.guardar(muchas));
    }
}
