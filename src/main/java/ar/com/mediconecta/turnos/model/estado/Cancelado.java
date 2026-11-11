package ar.com.mediconecta.turnos.model.estado;

import ar.com.mediconecta.turnos.model.EstadoTurno;

/** Estado final: ninguna transicion es valida. */
final class Cancelado implements EstadoDelTurno {

    static final Cancelado INSTANCIA = new Cancelado();

    private Cancelado() {}

    @Override
    public EstadoTurno nombre() {
        return EstadoTurno.CANCELADO;
    }
}
