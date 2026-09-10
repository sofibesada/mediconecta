package ar.com.mediconecta.turnos.business;

import ar.com.mediconecta.turnos.model.Turno;
import java.time.LocalDateTime;
import java.util.List;

public interface ITurnoService {
    List<Turno> consultarDisponibilidad(Long profesionalId);
    List<Turno> listarTurnosDePaciente(Long pacienteId);
    List<Turno> listarTurnosDeProfesional(Long profesionalId);
    Turno reservarTemporalmente(Long pacienteId, Long turnoId);
    Turno confirmarTurno(Long turnoId);
    void cancelarTurno(Long turnoId);
    Turno reprogramarTurno(Long turnoId, LocalDateTime nuevaFecha);
    Turno crearTurnoDisponible(Long profesionalId, LocalDateTime fechaHora);
}
