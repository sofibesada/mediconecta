package ar.com.mediconecta.turnos.model.estado;

import ar.com.mediconecta.turnos.model.EstadoTurno;
import ar.com.mediconecta.turnos.model.Turno;
import java.time.LocalDateTime;
import java.util.function.Predicate;

/**
 * Un paciente lo retuvo y tiene unos minutos para confirmarlo. Si la reserva vencio, el
 * horario vuelve a estar libre: otro paciente lo puede reservar.
 */
final class ReservadoTemporal implements EstadoDelTurno {

    static final ReservadoTemporal INSTANCIA = new ReservadoTemporal();

    private ReservadoTemporal() {}

    @Override
    public EstadoTurno nombre() {
        return EstadoTurno.RESERVADO_TEMPORAL;
    }

    @Override
    public void reservar(Turno turno, Long pacienteId, LocalDateTime holdHasta) {
        if (!turno.holdVencido()) {
            throw new TransicionInvalidaException("El turno no está disponible");
        }
        Disponible.INSTANCIA.reservar(turno, pacienteId, holdHasta);
    }

    @Override
    public void confirmar(Turno turno) {
        if (turno.holdVencido()) {
            throw new TransicionInvalidaException("El hold del turno expiró, volvé a reservarlo");
        }
        turno.setEstado(EstadoTurno.CONFIRMADO);
    }

    @Override
    public void liberar(Turno turno) {
        turno.setPacienteId(null);
        turno.setHoldExpiraEn(null);
        turno.setEstado(EstadoTurno.DISPONIBLE);
    }

    @Override
    public void cancelar(Turno turno, Predicate<Turno> politica) {
        EstadoDelTurno.cancelarSiCorresponde(turno, politica);
    }

    @Override
    public void reprogramar(Turno turno, LocalDateTime nuevaFecha) {
        throw new TransicionInvalidaException("El paciente está confirmando este turno; esperá a que lo confirme o se libere");
    }
}
