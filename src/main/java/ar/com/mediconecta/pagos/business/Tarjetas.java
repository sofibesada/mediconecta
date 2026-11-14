package ar.com.mediconecta.pagos.business;

import java.time.YearMonth;

/**
 * Validacion de la tarjeta ANTES de mandarla a la pasarela, con las mismas reglas que una
 * pasarela real: marca por el prefijo (BIN), largo, digito verificador (Luhn), vencimiento y
 * codigo de seguridad. Asi un error de tipeo se avisa enseguida.
 */
public final class Tarjetas {

    private Tarjetas() {}

    /** Marca segun el prefijo, o null si no se reconoce. */
    public static String marca(String numero) {
        String n = soloDigitos(numero);
        if (n.matches("3[47]\\d*")) return "American Express";
        if (n.matches("3(0[0-5]|[689])\\d*")) return "Diners Club";
        if (n.matches("589562\\d*")) return "Naranja";
        if (n.matches("(589657|6042|6043|6271)\\d*")) return "Cabal";
        if (n.matches("(5018|5020|5038|6304|6759|676[1-3])\\d*")) return "Maestro";
        if (n.matches("(5[1-5]|222[1-9]|22[3-9]\\d|2[3-6]\\d{2}|27[01]\\d|2720)\\d*")) return "Mastercard";
        if (n.matches("4\\d*")) return "Visa";
        return null;
    }

    public static boolean largoValido(String marca, int largo) {
        return switch (marca) {
            case "American Express" -> largo == 15;
            case "Diners Club" -> largo == 14;
            case "Maestro" -> largo >= 12 && largo <= 19;
            case "Visa" -> largo == 13 || largo == 16 || largo == 19;
            default -> largo == 16;
        };
    }

    /** Digito verificador de Luhn. */
    public static boolean luhn(String numero) {
        String n = soloDigitos(numero);
        if (n.isEmpty() || !n.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < n.length(); i++) {
            int d = n.charAt(n.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            suma += d;
        }
        return suma % 10 == 0;
    }

    /** Vencimiento MM/AA, valido hasta el ultimo dia de ese mes. */
    public static boolean vigente(String vencimiento, YearMonth hoy) {
        if (vencimiento == null || !vencimiento.matches("(0[1-9]|1[0-2])/\\d{2}")) {
            return false;
        }
        YearMonth v = YearMonth.of(2000 + Integer.parseInt(vencimiento.substring(3)), Integer.parseInt(vencimiento.substring(0, 2)));
        return !v.isBefore(hoy);
    }

    /**
     * Valida todos los datos y devuelve la marca. Lanza IllegalArgumentException con un mensaje
     * para el paciente si algo no es valido.
     */
    public static String validar(String numero, String vencimiento, String cvv, String titular, YearMonth hoy) {
        String n = soloDigitos(numero);
        String marca = marca(n);
        if (marca == null || !largoValido(marca, n.length()) || !luhn(n)) {
            throw new IllegalArgumentException("El número de tarjeta no es válido");
        }
        if (!vigente(vencimiento, hoy)) {
            throw new IllegalArgumentException("La tarjeta está vencida o la fecha no es válida (MM/AA)");
        }
        boolean amex = "American Express".equals(marca);
        if (cvv == null || !cvv.matches(amex ? "\\d{4}" : "\\d{3}")) {
            throw new IllegalArgumentException(amex ? "El código de seguridad de American Express tiene 4 dígitos"
                    : "El código de seguridad tiene 3 dígitos");
        }
        if (titular == null || titular.isBlank()) {
            throw new IllegalArgumentException("Ingresá el nombre del titular como figura en la tarjeta");
        }
        return marca;
    }

    public static String soloDigitos(String s) {
        return s == null ? "" : s.replaceAll("[\\s-]", "");
    }
}
