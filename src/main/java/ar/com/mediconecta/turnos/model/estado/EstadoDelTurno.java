package ar.com.mediconecta.turnos.model.estado;

import ar.com.mediconecta.turnos.model.EstadoTurno;
import ar.com.mediconecta.turnos.model.Turno;
import java.time.LocalDateTime;
import java.util.function.Predicate;

/**
 * Patron State: lo que se puede hacer con un turno depende de su estado.
 *
 * Cada estado implementa solo las transiciones que permite; las demas caen en los metodos
 * por defecto, que lanzan TransicionInvalidaException (la API responde 409). El Turno
 * guarda su estado como enum (es lo que persiste JPA) y delega en la clase que corresponde.
 *
 *   DISPONIBLE --reservar--> RESERVADO_TEMPORAL --confirmar--> CONFIRMADO
 *        ^                        |                               |
 *        +--------liberar---------+                               |
 *   DISPONIBLE / RESERVADO_TEMPORAL / CONFIRMADO --cancelar--> CANCELADO
 *
 * Un CONFIRMADO cuya fecha ya paso se considera FINALIZADO: no se cancela ni se reprograma.
 */
public interface EstadoDelTurno {

    EstadoTurno nombre();

    default void reservar(Turno turno, Long pacienteId, LocalDateTime holdHasta) {
        throw new TransicionInvalidaException("El turno no está disponible");
    }

    default void confirmar(Turno turno) {
        throw new TransicionInvalidaException("El turno no tiene una reserva temporal activa");
    }

    default void liberar(Turno turno) {
        throw new TransicionInvalidaException("El turno no tiene una reserva temporal para liberar");
    }

    /** politica: la regla de anticipacion (Strategy) que decide si todavia se puede cancelar. */
    default void cancelar(Turno turno, Predicate<Turno> politica) {
        throw new TransicionInvalidaException("El turno ya está cancelado");
    }

    default void reprogramar(Turno turno, LocalDateTime nuevaFecha) {
        throw new TransicionInvalidaException("No se puede reprogramar un turno " + nombre().name().toLowerCase().replace('_', ' '));
    }

    static EstadoDelTurno de(EstadoTurno estado) {
        return switch (estado) {
            case DISPONIBLE -> Disponible.INSTANCIA;
            case RESERVADO_TEMPORAL -> ReservadoTemporal.INSTANCIA;
            case CONFIRMADO -> Confirmado.INSTANCIA;
            case CANCELADO -> Cancelado.INSTANCIA;
        };
    }

    /** Pasos comunes a los estados que se pueden cancelar. */
    static void cancelarSiCorresponde(Turno turno, Predicate<Turno> politica) {
        if (!politica.test(turno)) {
            throw new TransicionInvalidaException("No se puede cancelar: faltan menos de 24hs para el turno");
        }
        turno.setEstado(EstadoTurno.CANCELADO);
    }
}
