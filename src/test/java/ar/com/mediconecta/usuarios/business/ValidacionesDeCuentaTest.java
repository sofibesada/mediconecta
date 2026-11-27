package ar.com.mediconecta.usuarios.business;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Validaciones de nombre, email y contraseña de una cuenta (D-16, lo encontró OWASP ZAP). */
class ValidacionesDeCuentaTest {

    @Test
    void unNombreYUnEmailNormalesPasan() {
        assertDoesNotThrow(() -> UsuarioService.validarDatosDeCuenta("Ana Pérez", "ana@mail.com"));
        assertDoesNotThrow(() -> UsuarioService.validarDatosDeCuenta("x".repeat(80), "a@b.co"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-es-email", "ana@", "@mail.com", "ana@mail", "ana mail@x.com", ""})
    void unEmailInvalidoSeRechaza(String email) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> UsuarioService.validarDatosDeCuenta("Ana", email));
        assertEquals("El email no es válido", ex.getMessage());
    }

    @Test
    void textosDemasiadoLargosSeRechazanAntesDeLlegarALaBase() {
        assertEquals("El nombre puede tener hasta 80 caracteres", assertThrows(IllegalArgumentException.class,
                () -> UsuarioService.validarDatosDeCuenta("n".repeat(81), "ana@mail.com")).getMessage());
        String emailLargo = "e".repeat(250) + "@x.com";
        assertThrows(IllegalArgumentException.class, () -> UsuarioService.validarDatosDeCuenta("Ana", emailLargo));
        assertThrows(IllegalArgumentException.class, () -> UsuarioService.validarDatosDeCuenta(" ", "ana@mail.com"));
    }

    @Test
    void laContrasenaTieneComoMaximo72Bytes() {
        assertDoesNotThrow(() -> UsuarioService.validarLargoDePassword("p".repeat(72)));
        assertEquals("La contraseña puede tener hasta 72 caracteres", assertThrows(IllegalArgumentException.class,
                () -> UsuarioService.validarLargoDePassword("p".repeat(73))).getMessage());
        // Se cuentan bytes: 40 «ñ» son 80 bytes en UTF-8.
        assertThrows(IllegalArgumentException.class, () -> UsuarioService.validarLargoDePassword("ñ".repeat(40)));
    }
}
