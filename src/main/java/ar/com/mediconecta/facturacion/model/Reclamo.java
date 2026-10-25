package ar.com.mediconecta.facturacion.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Reclamo (factura) de una consulta autorizada a la obra social. Nace PENDIENTE en la
 * misma transaccion que confirma el turno, y lo procesa el consumidor de la cola de
 * facturacion: ENVIADO si el legado lo acepto, RECHAZADO si no.
 */
@Entity
@Table(name = "reclamos")
public class Reclamo {

    public enum Estado { PENDIENTE, ENVIADO, RECHAZADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turno_id", nullable = false, unique = true)
    private Long turnoId;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "obra_social", nullable = false, length = 30)
    private String obraSocial;

    @Column(name = "numero_afiliado", nullable = false, length = 20)
    private String numeroAfiliado;

    @Column(name = "codigo_autorizacion", nullable = false, length = 30)
    private String codigoAutorizacion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal importe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "numero_reclamo", length = 30)
    private String numeroReclamo;

    @Column(length = 200)
    private String motivo;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "procesado_en")
    private LocalDateTime procesadoEn;

    @Version
    private Long version;

    public Long getId() { return id; }
    public Long getTurnoId() { return turnoId; }
    public void setTurnoId(Long turnoId) { this.turnoId = turnoId; }
    public Long getPacienteId() { return pacienteId; }
    public void setPacienteId(Long pacienteId) { this.pacienteId = pacienteId; }
    public String getObraSocial() { return obraSocial; }
    public void setObraSocial(String obraSocial) { this.obraSocial = obraSocial; }
    public String getNumeroAfiliado() { return numeroAfiliado; }
    public void setNumeroAfiliado(String numeroAfiliado) { this.numeroAfiliado = numeroAfiliado; }
    public String getCodigoAutorizacion() { return codigoAutorizacion; }
    public void setCodigoAutorizacion(String codigoAutorizacion) { this.codigoAutorizacion = codigoAutorizacion; }
    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public String getNumeroReclamo() { return numeroReclamo; }
    public void setNumeroReclamo(String numeroReclamo) { this.numeroReclamo = numeroReclamo; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
    public LocalDateTime getProcesadoEn() { return procesadoEn; }
    public void setProcesadoEn(LocalDateTime procesadoEn) { this.procesadoEn = procesadoEn; }
}
