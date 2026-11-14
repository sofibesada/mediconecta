package ar.com.mediconecta.turnos.business;

import ar.com.mediconecta.turnos.model.Turno;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public class PoliticaCancelacionEstandar implements PoliticaCancelacion {
    @Override
    public boolean puedeCancelarse(Turno turno) {
        // La fecha del turno se guarda sin zona, en hora de la clinica (la del servidor): se le
        // asigna esa zona para que el calculo sea correcto aunque haya cambio de horario.
        ZoneId zona = ZoneId.systemDefault();
        long horasRestantes = ChronoUnit.HOURS.between(ZonedDateTime.now(zona), turno.getFechaHora().atZone(zona));
        return horasRestantes >= 24;
    }
}