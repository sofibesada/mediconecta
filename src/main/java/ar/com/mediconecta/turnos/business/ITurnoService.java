package ar.com.mediconecta.turnos.business;

import ar.com.mediconecta.turnos.model.Modalidad;
import ar.com.mediconecta.turnos.model.Turno;
import java.time.LocalDateTime;
import java.util.List;

public interface ITurnoService {
    List<Turno> consultarDisponibilidad(Long profesionalId);
    List<Turno> listarTurnosDePaciente(Long pacienteId);
    List<Turno> listarTurnosDeProfesional(Long profesionalId);
    Turno buscarTurno(Long turnoId);
    Turno reservarTemporalmente(Long pacienteId, Long turnoId);
    /** Devuelve a DISPONIBLE la reserva temporal del paciente (si sigue siendo suya y no se confirmo). */
    void liberarReservaTemporal(Long pacienteId, Long turnoId);
    Turno confirmarTurno(Long turnoId);
    void cancelarTurno(Long turnoId);
    Turno reprogramarTurno(Long profesionalId, Long turnoId, LocalDateTime nuevaFecha);
    Turno crearTurnoDisponible(Long profesionalId, LocalDateTime fechaHora, Modalidad modalidad);
}
