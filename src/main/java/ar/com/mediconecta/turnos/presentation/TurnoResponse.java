package ar.com.mediconecta.turnos.presentation;

import ar.com.mediconecta.turnos.model.Turno;
import java.time.LocalDateTime;

public class TurnoResponse {
    public Long id;
    public Long profesionalId;
    public String profesionalNombre;
    public Long pacienteId;
    public String pacienteNombre;
    public LocalDateTime fechaHora;
    public String estado;
    public LocalDateTime holdExpiraEn;
    public boolean urgente;

    public static TurnoResponse from(Turno t, String profesionalNombre, String pacienteNombre) {
        TurnoResponse r = new TurnoResponse();
        r.id = t.getId();
        r.profesionalId = t.getProfesionalId();
        r.profesionalNombre = profesionalNombre;
        r.pacienteId = t.getPacienteId();
        r.pacienteNombre = pacienteNombre;
        r.fechaHora = t.getFechaHora();
        r.estado = t.getEstado() != null ? t.getEstado().name() : null;
        r.holdExpiraEn = t.getHoldExpiraEn();
        r.urgente = t.isUrgente();
        return r;
    }
}
