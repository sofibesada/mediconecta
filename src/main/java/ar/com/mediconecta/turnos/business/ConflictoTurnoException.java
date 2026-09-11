package ar.com.mediconecta.turnos.business;

import jakarta.ejb.ApplicationException;

/**
 * Rechazo de una operacion de turnos por una regla de negocio (turno no
 * disponible, hold vencido, fuera de plazo para cancelar, turno inexistente).
 *
 * rollback = false: a diferencia de una excepcion de sistema, esto no hace
 * que el EJB contenedor descarte la instancia ni revierta la transaccion.
 * Es necesario porque confirmarTurno(), cuando el hold vencio, primero
 * libera el turno (estado = DISPONIBLE) y recien despues rechaza la
 * operacion; con rollback = true (o con una excepcion de sistema, que es
 * lo que habia antes) esa liberacion se deshacia y el turno quedaba
 * atascado en RESERVADO_TEMPORAL para siempre.
 */
@ApplicationException(rollback = false)
public class ConflictoTurnoException extends IllegalStateException {
    public ConflictoTurnoException(String message) {
        super(message);
    }
}
