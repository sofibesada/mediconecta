package ar.com.mediconecta.notificaciones.business;

import ar.com.mediconecta.notificaciones.model.Notificacion;
import ar.com.mediconecta.turnos.eventos.TurnoEvento;
import java.util.List;

public interface INotificacionService {

    /** Convierte un evento de turno en notificaciones para paciente y profesional. */
    void procesarEventoTurno(TurnoEvento evento);

    List<Notificacion> listarDeUsuario(Long usuarioId);
    long contarNoLeidas(Long usuarioId);
    void marcarLeida(Long usuarioId, Long notificacionId);
    void marcarTodasLeidas(Long usuarioId);
}