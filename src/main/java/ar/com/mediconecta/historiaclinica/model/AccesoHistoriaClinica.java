package ar.com.mediconecta.historiaclinica.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Registro de auditoria de una consulta a datos clinicos (SEC-09): quien consulto, que
 * recurso y de que paciente, cuando y si se le permitio. Solo identificadores: nunca
 * diagnosticos, recetas, tokens ni el contenido de la ficha.
 */
@Entity
@Table(name = "auditoria_historia_clinica", indexes = {
        @Index(name = "ix_auditoria_hc_paciente", columnList = "pacienteId, fechaHora"),
        @Index(name = "ix_auditoria_hc_usuario", columnList = "usuarioId, fechaHora")})
public class AccesoHistoriaClinica {

    /** Que se consulto. */
    public enum Recurso { HISTORIA_CLINICA, FICHA_PACIENTE }

    public enum Resultado { AUTORIZADO, DENEGADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Quien consulto (id de usuario) y con que rol. */
    @Column(nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 20)
    private String rol;

    /** De que paciente es el recurso consultado. */
    @Column(nullable = false)
    private Long pacienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Recurso recurso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Resultado resultado;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public Long getPacienteId() { return pacienteId; }
    public void setPacienteId(Long pacienteId) { this.pacienteId = pacienteId; }
    public Recurso getRecurso() { return recurso; }
    public void setRecurso(Recurso recurso) { this.recurso = recurso; }
    public Resultado getResultado() { return resultado; }
    public void setResultado(Resultado resultado) { this.resultado = resultado; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
}
