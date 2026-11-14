package ar.com.mediconecta.seguridad;

import ar.com.mediconecta.config.Config;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifrado de datos clinicos en reposo (SEC-10) con AES-256-GCM.
 *
 * Formato guardado: "v1:" + Base64(IV de 12 bytes + texto cifrado + tag de 16 bytes). GCM
 * ademas de cifrar autentica: si alguien modifica el dato en la base, descifrar falla en
 * lugar de devolver basura. El IV es aleatorio en cada cifrado, asi dos diagnosticos
 * iguales no se ven iguales en la base. El prefijo "v1" deja lugar para rotar la clave.
 *
 * La clave (32 bytes en Base64) NO esta en el repositorio: se lee de mediconecta.datos.clave
 * (system property) o MEDICONECTA_CLAVE_DATOS (variable de entorno). Sin clave no se guarda
 * nada: es preferible que falle a que guarde datos clinicos sin cifrar.
 */
public final class CifradoDatos {

    public static final String PREFIJO = "v1:";
    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int LARGO_IV = 12;
    private static final int LARGO_TAG_BITS = 128;
    private static final int LARGO_CLAVE = 32;

    private static volatile CifradoDatos configurado;

    private final SecretKey clave;
    private final SecureRandom random = new SecureRandom();

    CifradoDatos(byte[] clave) {
        if (clave == null || clave.length != LARGO_CLAVE) {
            throw new IllegalStateException("La clave de datos tiene que ser de 32 bytes (256 bits), en Base64");
        }
        this.clave = new SecretKeySpec(clave, "AES");
    }

    /** El cifrador con la clave configurada (se lee una sola vez). */
    public static CifradoDatos configurado() {
        CifradoDatos c = configurado;
        if (c == null) {
            synchronized (CifradoDatos.class) {
                c = configurado;
                if (c == null) {
                    c = desdeBase64(Config.valor("mediconecta.datos.clave", "MEDICONECTA_CLAVE_DATOS"));
                    configurado = c;
                }
            }
        }
        return c;
    }

    public static CifradoDatos desdeBase64(String claveBase64) {
        if (claveBase64 == null) {
            throw new IllegalStateException("Falta la clave para cifrar los datos clínicos (MEDICONECTA_CLAVE_DATOS)");
        }
        try {
            return new CifradoDatos(Base64.getDecoder().decode(claveBase64.trim()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("La clave de datos (MEDICONECTA_CLAVE_DATOS) no es Base64 válido", e);
        }
    }

    /** True si el valor ya esta en el formato cifrado. */
    public static boolean estaCifrado(String valor) {
        return valor != null && valor.startsWith(PREFIJO);
    }

    public String cifrar(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            byte[] iv = new byte[LARGO_IV];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(LARGO_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] todo = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return PREFIJO + Base64.getEncoder().encodeToString(todo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el dato clínico", e);
        }
    }

    /**
     * Descifra un valor guardado. Un valor sin el prefijo es un dato anterior al cifrado: se
     * devuelve tal cual (al arrancar, MigracionCifradoClinico los cifra a todos).
     */
    public String descifrar(String guardado) {
        if (guardado == null || !estaCifrado(guardado)) {
            return guardado;
        }
        byte[] todo;
        try {
            todo = Base64.getDecoder().decode(guardado.substring(PREFIJO.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Un dato clínico cifrado está dañado", e);
        }
        if (todo.length < LARGO_IV + LARGO_TAG_BITS / 8) {
            throw new IllegalStateException("Un dato clínico cifrado está dañado");
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(LARGO_TAG_BITS, todo, 0, LARGO_IV));
            return new String(cipher.doFinal(todo, LARGO_IV, todo.length - LARGO_IV), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new IllegalStateException("No se pudo descifrar un dato clínico: la clave no coincide o el dato fue modificado", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo descifrar el dato clínico", e);
        }
    }
}
