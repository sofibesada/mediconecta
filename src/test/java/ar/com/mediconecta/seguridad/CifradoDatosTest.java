package ar.com.mediconecta.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class CifradoDatosTest {

    // Claves de prueba generadas para este test (no son de ningun entorno).
    static final String CLAVE = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTRA_CLAVE = Base64.getEncoder().encodeToString("una-clave-distinta-de-32-bytes!!".getBytes());

    private final CifradoDatos cifrado = CifradoDatos.desdeBase64(CLAVE);

    @Test
    void cifraYDescifraSinPerderNada() {
        String diagnostico = "Hipertensión arterial — control en 30 días ñ 😀";
        String guardado = cifrado.cifrar(diagnostico);

        assertTrue(guardado.startsWith(CifradoDatos.PREFIJO));
        assertFalse(guardado.contains("Hipertensión"), "en la base no se lee el texto");
        assertEquals(diagnostico, cifrado.descifrar(guardado));
    }

    @Test
    void elMismoTextoNoQuedaIgualDosVeces() {
        assertNotEquals(cifrado.cifrar("Gripe"), cifrado.cifrar("Gripe"));
    }

    @Test
    void nulosYDatosViejosSinCifrar() {
        assertNull(cifrado.cifrar(null));
        assertNull(cifrado.descifrar(null));
        assertEquals("dato anterior al cifrado", cifrado.descifrar("dato anterior al cifrado"));
        assertTrue(CifradoDatos.estaCifrado("v1:abc"));
        assertFalse(CifradoDatos.estaCifrado("texto"));
        assertFalse(CifradoDatos.estaCifrado(null));
    }

    @Test
    void unDatoModificadoEnLaBaseNoSeDescifra() {
        String guardado = cifrado.cifrar("Diabetes tipo 2");
        byte[] bytes = Base64.getDecoder().decode(guardado.substring(3));
        bytes[bytes.length - 1] ^= 1;
        String alterado = CifradoDatos.PREFIJO + Base64.getEncoder().encodeToString(bytes);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> cifrado.descifrar(alterado));
        assertTrue(e.getMessage().contains("modificado"));
    }

    @Test
    void conOtraClaveNoSeDescifra() {
        String guardado = cifrado.cifrar("Asma");
        assertThrows(IllegalStateException.class, () -> CifradoDatos.desdeBase64(OTRA_CLAVE).descifrar(guardado));
    }

    @Test
    void datosDanados() {
        assertThrows(IllegalStateException.class, () -> cifrado.descifrar("v1:no-es-base64!!"));
        assertThrows(IllegalStateException.class, () -> cifrado.descifrar("v1:" + Base64.getEncoder().encodeToString(new byte[10])));
    }

    @Test
    void laClaveTieneQueSerDe256Bits() {
        assertThrows(IllegalStateException.class, () -> CifradoDatos.desdeBase64(null));
        assertThrows(IllegalStateException.class, () -> CifradoDatos.desdeBase64("no es base64 ###"));
        assertThrows(IllegalStateException.class,
                () -> CifradoDatos.desdeBase64(Base64.getEncoder().encodeToString(new byte[16])));
        assertThrows(IllegalStateException.class, () -> new CifradoDatos(null));
    }

    @Test
    void laClaveConfiguradaSeLeeUnaSolaVez() {
        String anterior = System.getProperty("mediconecta.datos.clave");
        System.setProperty("mediconecta.datos.clave", CLAVE);
        try {
            CifradoDatos c = CifradoDatos.configurado();
            assertSame(c, CifradoDatos.configurado());
            assertEquals("Fiebre", cifrado.descifrar(c.cifrar("Fiebre")));
        } finally {
            if (anterior == null) {
                System.clearProperty("mediconecta.datos.clave");
            } else {
                System.setProperty("mediconecta.datos.clave", anterior);
            }
        }
    }

    @Test
    void elConverterDeJpaCifraAlGuardarYDescifraAlLeer() {
        CampoClinicoCifrado converter = new CampoClinicoCifrado(cifrado);
        String enLaBase = converter.convertToDatabaseColumn("Receta: Amoxicilina 500 mg");

        assertFalse(enLaBase.contains("Amoxicilina"));
        assertEquals("Receta: Amoxicilina 500 mg", converter.convertToEntityAttribute(enLaBase));
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
