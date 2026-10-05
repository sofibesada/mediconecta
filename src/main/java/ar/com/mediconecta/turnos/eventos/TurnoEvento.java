package ar.com.mediconecta.turnos.eventos;

import java.time.LocalDateTime;

public class TurnoEvento {

    private String eventoId;
    private TipoEventoTurno tipo;
    private Long turnoId;
    private Long pacienteId;
    private Long profesionalId;
    private LocalDateTime fechaHora;

    private LocalDateTime fechaHoraAnterior;
    private LocalDateTime ocurridoEn;
    /** PRESENCIAL o VIRTUAL. String (no el enum) para que el contrato JSON no dependa de la clase. */
    private String modalidad;

    public TurnoEvento() {}

    public String getModalidad() { return modalidad; }
    public void setModalidad(String modalidad) { this.modalidad = modalidad; }
    public boolean esVirtual() { return "VIRTUAL".equals(modalidad); }

    public String getEventoId() { return eventoId; }
    public void setEventoId(String eventoId) { this.eventoId = eventoId; }
    public TipoEventoTurno getTipo() { return tipo; }
    public void setTipo(TipoEventoTurno tipo) { this.tipo = tipo; }
    public Long getTurnoId() { return turnoId; }
    public void setTurnoId(Long turnoId) { this.turnoId = turnoId; }
    public Long getPacienteId() { return pacienteId; }
    public void setPacienteId(Long pacienteId) { this.pacienteId = pacienteId; }
    public Long getProfesionalId() { return profesionalId; }
    public void setProfesionalId(Long profesionalId) { this.profesionalId = profesionalId; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
    public LocalDateTime getFechaHoraAnterior() { return fechaHoraAnterior; }
    public void setFechaHoraAnterior(LocalDateTime fechaHoraAnterior) { this.fechaHoraAnterior = fechaHoraAnterior; }
    public LocalDateTime getOcurridoEn() { return ocurridoEn; }
    public void setOcurridoEn(LocalDateTime ocurridoEn) { this.ocurridoEn = ocurridoEn; }
}
