package ar.com.mediconecta.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private static final String CLAVE = "clave-de-prueba-de-los-tests-unitarios-0123456789";
    private static final String HEADER = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");

    private TokenService service;

    @BeforeEach
    void preparar() {
        System.setProperty("mediconecta.jwt.secret", CLAVE);
        service = new TokenService();
        service.init();
    }

    @AfterEach
    void limpiar() {
        System.clearProperty("mediconecta.jwt.secret");
    }

    @Test
    void elTokenEmitidoEsValidoYDevuelveElUsuario() {
        assertEquals(42L, service.validar(service.emitir(42L)));
    }

    @Test
    void elTokenDura15Minutos() {
        String payload = new String(Base64.getUrlDecoder().decode(service.emitir(7L).split("\\.")[1]), StandardCharsets.UTF_8);
        long iat = Long.parseLong(payload.replaceAll(".*\"iat\":(\\d+).*", "$1"));
        long exp = Long.parseLong(payload.replaceAll(".*\"exp\":(\\d+).*", "$1"));
        assertEquals(900, exp - iat);
    }

    @Test
    void tokenNuloOMalFormado() {
        assertNull(service.validar(null));
        assertNull(service.validar("no-es-un-jwt"));
        assertNull(service.validar("a.b"));
        assertNull(service.validar(HEADER + ".%%%.firma"));
    }

    @Test
    void conOtroAlgoritmoSeRechaza() {
        String noneHeader = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = service.emitir(1L).split("\\.")[1];
        assertNull(service.validar(noneHeader + "." + payload + "."));
    }

    @Test
    void conLaFirmaAlteradaSeRechaza() {
        String[] partes = service.emitir(1L).split("\\.");
        String otroPayload = b64("{\"sub\":\"2\",\"iat\":1,\"exp\":" + (Instant.now().getEpochSecond() + 600) + "}");
        assertNull(service.validar(partes[0] + "." + otroPayload + "." + partes[2]));
    }

    @Test
    void vencidoSeRechaza() {
        long ahora = Instant.now().getEpochSecond();
        assertNull(service.validar(firmado("{\"sub\":\"5\",\"iat\":" + (ahora - 1000) + ",\"exp\":" + (ahora - 1) + "}")));
    }

    @Test
    void firmadoConOtraClaveSeRechaza() {
        TokenService otro = new TokenService();
        System.setProperty("mediconecta.jwt.secret", "otra-clave-distinta-de-al-menos-32-caracteres!!");
        otro.init();
        assertNull(service.validar(otro.emitir(1L)));
    }

    @Test
    void sinClaveConfiguradaUsaUnaAleatoria() throws Exception {
        System.setProperty("mediconecta.jwt.secret", "corta");
        TokenService a = new TokenService();
        a.init();
        TokenService b = new TokenService();
        b.init();
        assertNotEquals(new String(clave(a), StandardCharsets.ISO_8859_1), new String(clave(b), StandardCharsets.ISO_8859_1));
        assertEquals(3L, a.validar(a.emitir(3L)));
        assertNull(b.validar(a.emitir(3L)));
    }

    private static String firmado(String payloadJson) {
        try {
            String contenido = HEADER + "." + b64(payloadJson);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(CLAVE.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return contenido + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] clave(TokenService s) throws Exception {
        Field f = TokenService.class.getDeclaredField("clave");
        f.setAccessible(true);
        return (byte[]) f.get(s);
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
