package ar.com.mediconecta.turnos.model.estado;

import ar.com.mediconecta.turnos.model.EstadoTurno;
import ar.com.mediconecta.turnos.model.Turno;
import java.time.LocalDateTime;
import java.util.function.Predicate;

/** Horario publicado por el profesional, sin paciente: se puede reservar, cancelar o mover. */
final class Disponible implements EstadoDelTurno {

    static final Disponible INSTANCIA = new Disponible();

    private Disponible() {}

    @Override
    public EstadoTurno nombre() {
        return EstadoTurno.DISPONIBLE;
    }

    @Override
    public void reservar(Turno turno, Long pacienteId, LocalDateTime holdHasta) {
        if (turno.yaPaso()) {
            throw new TransicionInvalidaException("Ese horario ya pasó");
        }
        turno.setPacienteId(pacienteId);
        turno.setHoldExpiraEn(holdHasta);
        turno.setEstado(EstadoTurno.RESERVADO_TEMPORAL);
    }

    @Override
    public void cancelar(Turno turno, Predicate<Turno> politica) {
        EstadoDelTurno.cancelarSiCorresponde(turno, politica);
    }

    @Override
    public void reprogramar(Turno turno, LocalDateTime nuevaFecha) {
        turno.setFechaHora(nuevaFecha);
    }
}
