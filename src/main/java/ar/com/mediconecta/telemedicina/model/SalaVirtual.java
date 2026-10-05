package ar.com.mediconecta.telemedicina.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Sala de videollamada creada en la plataforma externa para un turno VIRTUAL. */
@Entity
@Table(name = "salas_virtuales")
public class SalaVirtual {

    public enum Estado { ACTIVA, ELIMINADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turno_id", nullable = false, unique = true)
    private Long turnoId;

    @Column(name = "paciente_id")
    private Long pacienteId;

    @Column(name = "profesional_id", nullable = false)
    private Long profesionalId;

    /** Id de la sala en la plataforma de videollamadas. */
    @Column(name = "sala_externa_id", nullable = false)
    private String salaExternaId;

    /** Link del paciente (invitado). */
    @Column(nullable = false, length = 500)
    private String url;

    /** Link del profesional (anfitrion). Nullable: las salas creadas antes no lo tienen. */
    @Column(name = "url_anfitrion", length = 500)
    private String urlAnfitrion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.ACTIVA;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    public SalaVirtual() {}

    public Long getId() { return id; }
    public Long getTurnoId() { return turnoId; }
    public void setTurnoId(Long turnoId) { this.turnoId = turnoId; }
    public Long getPacienteId() { return pacienteId; }
    public void setPacienteId(Long pacienteId) { this.pacienteId = pacienteId; }
    public Long getProfesionalId() { return profesionalId; }
    public void setProfesionalId(Long profesionalId) { this.profesionalId = profesionalId; }
    public String getSalaExternaId() { return salaExternaId; }
    public void setSalaExternaId(String salaExternaId) { this.salaExternaId = salaExternaId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public LocalDateTime getCreadaEn() { return creadaEn; }
    public void setCreadaEn(LocalDateTime creadaEn) { this.creadaEn = creadaEn; }

    public String getUrlAnfitrion() { return urlAnfitrion; }
    public void setUrlAnfitrion(String urlAnfitrion) { this.urlAnfitrion = urlAnfitrion; }

    public boolean esAnfitrion(Long usuarioId) {
        return usuarioId != null && usuarioId.equals(profesionalId);
    }

    /** Cada participante recibe SU link: el profesional el de anfitrion, el paciente el de invitado. */
    public String urlPara(Long usuarioId) {
        return esAnfitrion(usuarioId) && urlAnfitrion != null ? urlAnfitrion : url;
    }

    public boolean participa(Long usuarioId) {
        return usuarioId != null && (usuarioId.equals(pacienteId) || usuarioId.equals(profesionalId));
    }
}
