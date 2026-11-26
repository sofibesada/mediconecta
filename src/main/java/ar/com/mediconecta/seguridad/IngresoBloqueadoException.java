package ar.com.mediconecta.seguridad;

/** Demasiados intentos fallidos de ingreso a una cuenta (429, ver IntentosDeIngreso). */
public class IngresoBloqueadoException extends RuntimeException {

    private final long segundosParaReintentar;

    public IngresoBloqueadoException(long segundosParaReintentar) {
        super("Hubo demasiados intentos fallidos de ingreso a esta cuenta. Probá de nuevo en "
                + minutosOSegundos(segundosParaReintentar) + ".");
        this.segundosParaReintentar = segundosParaReintentar;
    }

    public long getSegundosParaReintentar() {
        return segundosParaReintentar;
    }

    static String minutosOSegundos(long segundos) {
        if (segundos < 60) {
            return segundos + (segundos == 1 ? " segundo" : " segundos");
        }
        long minutos = (segundos + 59) / 60;
        return minutos + (minutos == 1 ? " minuto" : " minutos");
    }
}
