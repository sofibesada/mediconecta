package ar.com.mediconecta.seguridad;

import ar.com.mediconecta.config.Config;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Emite y valida los tokens de sesion (JWT firmado con HMAC-SHA256).
 *
 * El token solo dice QUIEN es el usuario (sub = id) y hasta cuando vale (exp). El rol,
 * si esta activo y si debe cambiar la contrasena se leen de la base en cada pedido
 * (ver JwtAuthenticationMechanism), asi desactivar una cuenta corta el acceso en el acto.
 *
 * Clave de firma: system property mediconecta.jwt.secret (minimo 32 caracteres). Si no
 * esta configurada se genera una al azar al desplegar: sirve para desarrollo, pero los
 * tokens dejan de valer en cada redeploy y hay que volver a iniciar sesion.
 */
@ApplicationScoped
public class TokenService {

    private static final Logger LOG = Logger.getLogger(TokenService.class.getName());
    private static final Duration VIGENCIA = Duration.ofHours(8);
    private static final String HEADER = base64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private static final Jsonb JSONB = JsonbBuilder.create();

    private byte[] clave;

    @PostConstruct
    void init() {
        String configurada = Config.valor("mediconecta.jwt.secret", "MEDICONECTA_JWT_SECRET");
        if (configurada != null && configurada.length() >= 32) {
            clave = configurada.getBytes(StandardCharsets.UTF_8);
        } else {
            clave = new byte[32];
            new SecureRandom().nextBytes(clave);
            LOG.warning("[Seguridad] mediconecta.jwt.secret no configurada (o con menos de 32 caracteres): "
                    + "se usa una clave aleatoria y los tokens se invalidan en cada redeploy");
        }
    }

    public String emitir(Long usuarioId) {
        Instant ahora = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", String.valueOf(usuarioId));
        claims.put("iat", ahora.getEpochSecond());
        claims.put("exp", ahora.plus(VIGENCIA).getEpochSecond());
        String contenido = HEADER + "." + base64(JSONB.toJson(claims).getBytes(StandardCharsets.UTF_8));
        return contenido + "." + base64(firmar(contenido));
    }

    /** Devuelve el id del usuario si el token es valido y no vencio; si no, null. */
    public Long validar(String token) {
        if (token == null) {
            return null;
        }
        String[] partes = token.split("\\.");
        if (partes.length != 3 || !HEADER.equals(partes[0])) {
            return null;
        }
        try {
            // Se compara el texto de la firma (no los bytes decodificados) para no aceptar
            // variantes del base64, y en tiempo constante para no filtrar cuantos coinciden.
            String esperada = base64(firmar(partes[0] + "." + partes[1]));
            if (!MessageDigest.isEqual(esperada.getBytes(StandardCharsets.US_ASCII), partes[2].getBytes(StandardCharsets.US_ASCII))) {
                return null;
            }
            Map<?, ?> claims = JSONB.fromJson(new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8), Map.class);
            long exp = ((Number) claims.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= exp) {
                return null;
            }
            return Long.valueOf((String) claims.get("sub"));
        } catch (RuntimeException e) {
            return null; // token mal formado
        }
    }

    private byte[] firmar(String contenido) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }

    private static String base64(byte[] datos) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(datos);
    }
}
