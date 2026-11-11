package ar.com.mediconecta.turnos.model.estado;

import ar.com.mediconecta.turnos.model.EstadoTurno;
import ar.com.mediconecta.turnos.model.Turno;
import java.time.LocalDateTime;
import java.util.function.Predicate;

/** Turno confirmado. Una vez que paso la fecha queda finalizado: ya no se cancela ni se mueve. */
final class Confirmado implements EstadoDelTurno {

    static final Confirmado INSTANCIA = new Confirmado();

    private Confirmado() {}

    @Override
    public EstadoTurno nombre() {
        return EstadoTurno.CONFIRMADO;
    }

    @Override
    public void cancelar(Turno turno, Predicate<Turno> politica) {
        if (turno.yaPaso()) {
            throw new TransicionInvalidaException("No se puede cancelar: el turno ya finalizó");
        }
        EstadoDelTurno.cancelarSiCorresponde(turno, politica);
    }

    @Override
    public void reprogramar(Turno turno, LocalDateTime nuevaFecha) {
        if (turno.yaPaso()) {
            throw new TransicionInvalidaException("No se puede reprogramar un turno que ya finalizó");
        }
        turno.setFechaHora(nuevaFecha);
    }
}
