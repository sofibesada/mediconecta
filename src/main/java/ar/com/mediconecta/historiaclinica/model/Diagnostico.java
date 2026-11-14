package ar.com.mediconecta.historiaclinica.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "diagnosticos")
public class Diagnostico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    /** Cifrado en la base (SEC-10). El largo de la columna es el del texto cifrado. */
    @Convert(converter = ar.com.mediconecta.seguridad.CampoClinicoCifrado.class)
    @Column(nullable = false, length = 8000)
    private String descripcion;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public Diagnostico() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPacienteId() { return pacienteId; }
    public void setPacienteId(Long pacienteId) { this.pacienteId = pacienteId; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}