package ar.com.mediconecta.pagos.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TarjetasTest {

    private static final YearMonth HOY = YearMonth.of(2026, 11);

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "4111111111111111, Visa",
            "5555555555554444, Mastercard",
            "2221000000000009, Mastercard",
            "378282246310005, American Express",
            "30569309025904, Diners Club",
            "5895620000000002, Naranja",
            "6042010000000006, Cabal",
            "6759649826438453, Maestro",
    })
    void reconoceLaMarcaPorElPrefijo(String numero, String marca) {
        assertEquals(marca, Tarjetas.marca(numero));
    }

    @Test
    void unPrefijoDesconocidoNoTieneMarca() {
        assertNull(Tarjetas.marca("9999999999999999"));
        assertNull(Tarjetas.marca(null));
    }

    @Test
    void largosPorMarca() {
        assertTrue(Tarjetas.largoValido("American Express", 15));
        assertFalse(Tarjetas.largoValido("American Express", 16));
        assertTrue(Tarjetas.largoValido("Diners Club", 14));
        assertTrue(Tarjetas.largoValido("Maestro", 12));
        assertFalse(Tarjetas.largoValido("Maestro", 20));
        assertTrue(Tarjetas.largoValido("Visa", 13));
        assertTrue(Tarjetas.largoValido("Visa", 19));
        assertFalse(Tarjetas.largoValido("Visa", 15));
        assertTrue(Tarjetas.largoValido("Mastercard", 16));
        assertFalse(Tarjetas.largoValido("Mastercard", 15));
    }

    @Test
    void digitoVerificadorDeLuhn() {
        assertTrue(Tarjetas.luhn("4111 1111 1111 1111"));
        assertTrue(Tarjetas.luhn("4111-1111-1111-1111"));
        assertFalse(Tarjetas.luhn("4111111111111112"));
        assertFalse(Tarjetas.luhn(""));
        assertFalse(Tarjetas.luhn("4111abcd"));
    }

    @Test
    void vencimientoHastaElUltimoDiaDelMes() {
        assertTrue(Tarjetas.vigente("11/26", HOY));
        assertTrue(Tarjetas.vigente("01/30", HOY));
        assertFalse(Tarjetas.vigente("10/26", HOY));
        assertFalse(Tarjetas.vigente("13/27", HOY));
        assertFalse(Tarjetas.vigente("1127", HOY));
        assertFalse(Tarjetas.vigente(null, HOY));
    }

    @Test
    void validarDevuelveLaMarcaSiTodoEstaBien() {
        assertEquals("Visa", Tarjetas.validar("4111 1111 1111 1111", "12/28", "123", "ANA PEREZ", HOY));
        assertEquals("American Express", Tarjetas.validar("378282246310005", "12/28", "1234", "ANA PEREZ", HOY));
    }

    @Test
    void validarExplicaCadaError() {
        assertEquals("El número de tarjeta no es válido",
                assertThrows(IllegalArgumentException.class, () -> Tarjetas.validar("4111111111111112", "12/28", "123", "A", HOY)).getMessage());
        assertEquals("El número de tarjeta no es válido",
                assertThrows(IllegalArgumentException.class, () -> Tarjetas.validar("9999999999999995", "12/28", "123", "A", HOY)).getMessage());
        assertEquals("El número de tarjeta no es válido",
                assertThrows(IllegalArgumentException.class, () -> Tarjetas.validar("411111111111", "12/28", "123", "A", HOY)).getMessage());
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> Tarjetas.validar("4111111111111111", "01/20", "123", "A", HOY)).getMessage().contains("vencida"));
        assertEquals("El código de seguridad tiene 3 dígitos",
                assertThrows(IllegalArgumentException.class, () -> Tarjetas.validar("4111111111111111", "12/28", "12", "A", HOY)).getMessage());
        assertEquals("El código de seguridad de American Express tiene 4 dígitos",
                assertThrows(IllegalArgumentException.class, () -> Tarjetas.validar("378282246310005", "12/28", "123", "A", HOY)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> Tarjetas.validar("4111111111111111", "12/28", null, "A", HOY));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> Tarjetas.validar("4111111111111111", "12/28", "123", " ", HOY)).getMessage().contains("titular"));
        assertThrows(IllegalArgumentException.class, () -> Tarjetas.validar("4111111111111111", "12/28", "123", null, HOY));
    }
}
