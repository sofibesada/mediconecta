package ar.com.mediconecta.ia.business;

import jakarta.ejb.ApplicationException;

/** El paciente supero las consultas permitidas por minuto al orientador (429). */
@ApplicationException(rollback = false)
public class LimiteDeConsultasException extends RuntimeException {

    private final long segundosParaReintentar;

    public LimiteDeConsultasException(long segundosParaReintentar) {
        super("Hiciste muchas consultas seguidas al orientador. Probá de nuevo en " + segundosParaReintentar + " segundos.");
        this.segundosParaReintentar = segundosParaReintentar;
    }

    public long getSegundosParaReintentar() {
        return segundosParaReintentar;
    }
}
