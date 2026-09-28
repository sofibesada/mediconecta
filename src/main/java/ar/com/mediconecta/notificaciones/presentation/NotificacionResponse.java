package ar.com.mediconecta.notificaciones.presentation;

import ar.com.mediconecta.notificaciones.model.Notificacion;
import java.time.LocalDateTime;

/**
 * Respuesta de la API para una notificacion. No expone eventoId, que es un
 * detalle interno de la mensajeria (idempotencia).
 */
public class NotificacionResponse {
    public Long id;
    public Long turnoId;
    public String tipo;
    public String titulo;
    public String mensaje;
    public LocalDateTime creadaEn;
    public boolean leida;

    public static NotificacionResponse from(Notificacion n) {
        NotificacionResponse r = new NotificacionResponse();
        r.id = n.getId();
        r.turnoId = n.getTurnoId();
        r.tipo = n.getTipo();
        r.titulo = n.getTitulo();
        r.mensaje = n.getMensaje();
        r.creadaEn = n.getCreadaEn();
        r.leida = n.isLeida();
        return r;
    }
}
