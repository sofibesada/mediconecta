package ar.com.mediconecta.obrassociales.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Registro de cada pedido de autorizacion a una obra social, haya salido bien o mal.
 * Se guarda en su propia transaccion (REQUIRES_NEW): queda aunque la confirmacion del
 * turno se revierta.
 */
@Entity
@Table(name = "autorizaciones_intentos")
public class IntentoAutorizacion {

    public enum Resultado { AUTORIZADA, RECHAZADA, SIN_RESPUESTA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turno_id", nullable = false)
    private Long turnoId;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "obra_social", nullable = false, length = 30)
    private String obraSocial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Resultado resultado;

    @Column(name = "codigo_autorizacion", length = 30)
    private String codigoAutorizacion;

    @Column(length = 200)
    private String motivo;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public Long getId() { return id; }
    public Long getTurnoId() { return turnoId; }
    public void setTurnoId(Long turnoId) { this.turnoId = turnoId; }
    public Long getPacienteId() { return pacienteId; }
    public void setPacienteId(Long pacienteId) { this.pacienteId = pacienteId; }
    public String getObraSocial() { return obraSocial; }
    public void setObraSocial(String obraSocial) { this.obraSocial = obraSocial; }
    public Resultado getResultado() { return resultado; }
    public void setResultado(Resultado resultado) { this.resultado = resultado; }
    public String getCodigoAutorizacion() { return codigoAutorizacion; }
    public void setCodigoAutorizacion(String codigoAutorizacion) { this.codigoAutorizacion = codigoAutorizacion; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
