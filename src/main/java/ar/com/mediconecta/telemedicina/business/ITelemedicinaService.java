package ar.com.mediconecta.telemedicina.business;

import ar.com.mediconecta.telemedicina.model.SalaVirtual;
import ar.com.mediconecta.turnos.eventos.TurnoEvento;

public interface ITelemedicinaService {

    /** Crea o elimina la sala segun el evento del turno (solo turnos VIRTUAL). */
    void procesarEventoTurno(TurnoEvento evento);

    /** Sala activa del turno, solo si quien pregunta es el paciente o el profesional. */
    SalaVirtual obtenerSala(Long turnoId, Long usuarioId);
}
