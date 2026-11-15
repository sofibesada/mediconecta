package ar.com.mediconecta.usuarios.business;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Cartilla de un profesional: las obras sociales y prepagas que atiende (sus convenios).
 *
 * Se guarda como texto en el usuario:
 *   null              atiende todas (es el valor por defecto de un profesional nuevo)
 *   ""                no atiende ninguna: solo turnos particulares
 *   "OSDE,SWISS"      solo esas (codigos del sistema de obras sociales)
 */
public final class Cartilla {

    private static final Pattern CODIGO = Pattern.compile("[A-Z0-9_]{2,20}");
    static final int LARGO_MAXIMO = 500;

    private Cartilla() {}

    /** True si el profesional con esa cartilla atiende esa obra social. */
    public static boolean atiende(String cartilla, String obraSocial) {
        if (cartilla == null) {
            return true;
        }
        if (obraSocial == null || obraSocial.isBlank()) {
            return false;
        }
        return lista(cartilla).contains(obraSocial.trim().toUpperCase(Locale.ROOT));
    }

    /** null = todas; lista vacia = ninguna (solo particular). */
    public static List<String> lista(String cartilla) {
        if (cartilla == null) {
            return null;
        }
        if (cartilla.isBlank()) {
            return List.of();
        }
        return Arrays.stream(cartilla.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** Valida y normaliza los codigos para guardarlos. null = todas. */
    public static String guardar(Collection<String> codigos) {
        if (codigos == null) {
            return null;
        }
        TreeSet<String> limpios = new TreeSet<>();
        for (String c : codigos) {
            String codigo = c == null ? "" : c.trim().toUpperCase(Locale.ROOT);
            if (!CODIGO.matcher(codigo).matches()) {
                throw new IllegalArgumentException("Código de obra social inválido: " + c);
            }
            limpios.add(codigo);
        }
        String texto = String.join(",", limpios);
        if (texto.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("La cartilla tiene demasiadas obras sociales");
        }
        return texto;
    }
}
