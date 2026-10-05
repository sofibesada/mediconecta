package ar.com.mediconecta.turnos.presentation;

import java.time.LocalDateTime;
import java.util.List;

/** Lo que el asistente (@Stateful) recuerda de la reserva en curso del paciente. */
public class EstadoAsistenteResponse {
    /** Identifica la instancia del bean: es la misma en todos los pedidos de una conversacion. */
    public int asistenteId;
    public LocalDateTime iniciadoEn;
    public int pedidosAtendidos;
    public Long profesionalId;
    public TurnoResponse turnoRetenido;
    /** Solo al elegir profesional: sus horarios disponibles. */
    public List<TurnoResponse> horarios;
}
