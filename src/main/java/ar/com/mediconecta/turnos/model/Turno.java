package ar.com.mediconecta.turnos.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "turnos")
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id")
    private Long pacienteId;

    @Column(name = "profesional_id", nullable = false)
    private Long profesionalId;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTurno estado;

    @Column(name = "hold_expira_en")
    private LocalDateTime holdExpiraEn;

    @Column(nullable = false)
    private boolean urgente = false;

    // Nullable a proposito: hbm2ddl=update no puede agregar una columna NOT NULL a una
    // tabla con filas. Los turnos anteriores (null) se tratan como PRESENCIAL.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Modalidad modalidad = Modalidad.PRESENCIAL;

    @Version
    private Long version;

    public Turno() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPacienteId() { return pacienteId; }
    public void setPacienteId(Long pacienteId) { this.pacienteId = pacienteId; }
    public Long getProfesionalId() { return profesionalId; }
    public void setProfesionalId(Long profesionalId) { this.profesionalId = profesionalId; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
    public EstadoTurno getEstado() { return estado; }
    public void setEstado(EstadoTurno estado) { this.estado = estado; }
    public LocalDateTime getHoldExpiraEn() { return holdExpiraEn; }
    public void setHoldExpiraEn(LocalDateTime holdExpiraEn) { this.holdExpiraEn = holdExpiraEn; }
    public boolean isUrgente() { return urgente; }
    public void setUrgente(boolean urgente) { this.urgente = urgente; }

    public Modalidad getModalidad() { return modalidad != null ? modalidad : Modalidad.PRESENCIAL; }
    public void setModalidad(Modalidad modalidad) { this.modalidad = modalidad; }

    /** La fecha del turno ya pasó. */
    public boolean yaPaso() {
        return fechaHora != null && fechaHora.isBefore(LocalDateTime.now());
    }

    /**
     * Un turno confirmado cuya fecha ya pasó se considera FINALIZADO. No se guarda en la
     * base: se calcula al momento a partir de la fecha, así nunca queda desactualizado.
     */
    public boolean isFinalizado() {
        return estado == EstadoTurno.CONFIRMADO && yaPaso();
    }
}
