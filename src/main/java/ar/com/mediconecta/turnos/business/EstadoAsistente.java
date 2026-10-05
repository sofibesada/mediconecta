package ar.com.mediconecta.turnos.business;

import ar.com.mediconecta.turnos.model.Turno;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Foto del estado conversacional del AsistenteAgendamiento en un momento dado. */
public class EstadoAsistente implements Serializable {
    private static final long serialVersionUID = 1L;

    public int asistenteId;
    public LocalDateTime iniciadoEn;
    public int pedidosAtendidos;
    public Long profesionalId;
    /** El turno que el paciente tiene retenido (hold) y todavia no confirmo; null si no hay. */
    public Turno turnoRetenido;
    /** Solo al elegir profesional: sus horarios disponibles. */
    public java.util.List<Turno> horarios;
}
