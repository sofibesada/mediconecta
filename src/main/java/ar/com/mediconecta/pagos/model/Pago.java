package ar.com.mediconecta.pagos.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un intento de pago de un turno con tarjeta. Se guardan tambien los rechazados (para que el
 * paciente y el administrador vean por que no se cobro). Nunca se guarda el numero completo
 * de la tarjeta ni el codigo de seguridad: solo la marca y los ultimos 4 digitos.
 */
@Entity
@Table(name = "pago")
public class Pago {

    public enum Estado { APROBADO, RECHAZADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turno_id", nullable = false)
    private Long turnoId;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal importe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(length = 30)
    private String marca;

    @Column(length = 4)
    private String ultimos4;

    /** Id del pago en la pasarela externa. */
    @Column(name = "id_externo", length = 60)
    private String idExterno;

    @Column(name = "codigo_autorizacion", length = 20)
    private String codigoAutorizacion;

    @Column(length = 200)
    private String motivo;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    public Long getId() { return id; }
    public Long getTurnoId() { return turnoId; }
    public void setTurnoId(Long turnoId) { this.turnoId = turnoId; }
    public Long getPacienteId() { return pacienteId; }
    public void setPacienteId(Long pacienteId) { this.pacienteId = pacienteId; }
    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public String getUltimos4() { return ultimos4; }
    public void setUltimos4(String ultimos4) { this.ultimos4 = ultimos4; }
    public String getIdExterno() { return idExterno; }
    public void setIdExterno(String idExterno) { this.idExterno = idExterno; }
    public String getCodigoAutorizacion() { return codigoAutorizacion; }
    public void setCodigoAutorizacion(String codigoAutorizacion) { this.codigoAutorizacion = codigoAutorizacion; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
