package ar.com.mediconecta.turnos.business;

import ar.com.mediconecta.turnos.model.Turno;

public class PoliticaCancelacionUrgencia implements PoliticaCancelacion {
    @Override
    public boolean puedeCancelarse(Turno turno) {
        return true;
    }
}