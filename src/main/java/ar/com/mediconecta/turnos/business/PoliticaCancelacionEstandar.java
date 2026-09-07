package ar.com.mediconecta.turnos.business;

import ar.com.mediconecta.turnos.model.Turno;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class PoliticaCancelacionEstandar implements PoliticaCancelacion {
    @Override
    public boolean puedeCancelarse(Turno turno) {
        long horasRestantes = ChronoUnit.HOURS.between(LocalDateTime.now(), turno.getFechaHora());
        return horasRestantes >= 24;
    }
}