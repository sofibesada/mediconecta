package ar.com.mediconecta.idempotencia.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Respuesta ya entregada para una Idempotency-Key. Si el cliente repite el pedido con la
 * misma clave (reintento por timeout, doble clic), se devuelve esta respuesta y la
 * operacion no se vuelve a ejecutar.
 */
@Entity
@Table(name = "idempotencia", uniqueConstraints = @UniqueConstraint(columnNames = {"clave", "usuario_id"}))
public class RegistroIdempotencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String clave;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Metodo y ruta del pedido original: la misma clave no se puede usar en otra operacion. */
    @Column(nullable = false, length = 200)
    private String operacion;

    @Column(name = "respuesta", nullable = false, columnDefinition = "TEXT")
    private String respuestaJson;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    public Long getId() { return id; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public String getOperacion() { return operacion; }
    public void setOperacion(String operacion) { this.operacion = operacion; }
    public String getRespuestaJson() { return respuestaJson; }
    public void setRespuestaJson(String respuestaJson) { this.respuestaJson = respuestaJson; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
