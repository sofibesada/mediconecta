package ar.com.mediconecta.turnos.business;

import ar.com.mediconecta.turnos.model.Turno;

public interface PoliticaCancelacion {
    boolean puedeCancelarse(Turno turno);
}