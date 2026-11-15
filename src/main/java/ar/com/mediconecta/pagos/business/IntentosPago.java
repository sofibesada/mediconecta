package ar.com.mediconecta.pagos.business;

import ar.com.mediconecta.pagos.data.PagoRepository;
import ar.com.mediconecta.pagos.integracion.PasarelaPagosClient;
import ar.com.mediconecta.pagos.model.Pago;
import ar.com.mediconecta.turnos.business.ITurnoService;
import ar.com.mediconecta.turnos.eventos.TipoEventoTurno;
import ar.com.mediconecta.turnos.eventos.TurnoEventoPublisher;
import ar.com.mediconecta.turnos.model.EstadoTurno;
import ar.com.mediconecta.turnos.model.Turno;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Intentos de pago con resultado incierto (la pasarela no respondio a tiempo, o MediConecta
 * no pudo guardar la respuesta). Cada metodo va en su PROPIA transaccion (REQUIRES_NEW): el
 * intento PENDIENTE queda registrado aunque el cobro termine en error, y la conciliacion de
 * un pago no depende de la transaccion de quien la pide.
 *
 * Conciliar = preguntarle a la pasarela como termino ese cobro:
 *  - aprobado: se registra; si el turno ya se cancelo, se devuelve en el momento;
 *  - sin rastro despues de la ventana de incertidumbre: FALLIDO (se puede volver a pagar);
 *  - sin rastro todavia: sigue PENDIENTE (bloquea otro cobro) y se reintenta despues.
 */
@Stateless
public class IntentosPago {

    private static final Logger LOG = Logger.getLogger(IntentosPago.class.getName());
    /** Segundos en que un intento sin respuesta todavia puede terminar aprobado en la pasarela. */
    static final long VENTANA_POR_DEFECTO = 60;

    @Inject
    private PagoRepository repository;

    @Inject
    private PasarelaPagosClient pasarela;

    @Inject
    private ITurnoService turnoService;

    @Inject
    private TurnoEventoPublisher eventoPublisher;

    static long ventanaSegundos() {
        return Long.getLong("mediconecta.pagos.ventanaSegundos", VENTANA_POR_DEFECTO);
    }

    /** Registra el intento ANTES de llamar a la pasarela. Devuelve su id. */
    @PermitAll
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Long registrarPendiente(Long turnoId, Long pacienteId, BigDecimal importe, String marca, String ultimos4) {
        Pago p = new Pago();
        p.setTurnoId(turnoId);
        p.setPacienteId(pacienteId);
        p.setImporte(importe);
        p.setEstado(Pago.Estado.PENDIENTE);
        p.setMarca(marca);
        p.setUltimos4(ultimos4);
        p.setCreadoEn(LocalDateTime.now());
        return repository.guardar(p).getId();
    }

    /** El cobro no se hizo con certeza (por ejemplo, la pasarela rechazo los datos): no bloquea. */
    @PermitAll
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void marcarFallido(Long pagoId, String motivo) {
        Pago p = repository.buscarPorId(pagoId);
        if (p != null && p.getEstado() == Pago.Estado.PENDIENTE) {
            p.setEstado(Pago.Estado.FALLIDO);
            p.setMotivo(motivo);
            repository.actualizar(p);
        }
    }

    /** Averigua como termino un intento PENDIENTE. Devuelve su estado despues de conciliar. */
    @PermitAll
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Pago.Estado conciliar(Long pagoId) {
        Pago p = repository.buscarPorId(pagoId);
        if (p == null || p.getEstado() != Pago.Estado.PENDIENTE) {
            return p == null ? null : p.getEstado();
        }
        Optional<PasarelaPagosClient.Resultado> aprobado = pasarela.aprobadoDe(PagoService.referencia(p.getTurnoId()));
        if (aprobado.isPresent()) {
            PasarelaPagosClient.Resultado r = aprobado.get();
            if (repository.existeConIdExterno(r.id())) {
                // Ese cobro ya lo registro otro intento: este quedo de mas.
                p.setEstado(Pago.Estado.FALLIDO);
                p.setMotivo("El cobro ya estaba registrado en otro intento");
                repository.actualizar(p);
                return p.getEstado();
            }
            p.setEstado(Pago.Estado.APROBADO);
            p.setIdExterno(r.id());
            p.setMarca(r.marca());
            p.setUltimos4(r.ultimos4());
            p.setCodigoAutorizacion(r.codigoAutorizacion());
            Turno turno = turnoService.buscarTurno(p.getTurnoId());
            if (turno != null && turno.getEstado() == EstadoTurno.CANCELADO) {
                // Se cancelo mientras el cobro estaba en el aire: se devuelve ahora.
                pasarela.reembolsar(r.id());
                p.setEstado(Pago.Estado.REEMBOLSADO);
                p.setReembolsadoEn(LocalDateTime.now());
                eventoPublisher.publicar(TipoEventoTurno.PAGO_REEMBOLSADO, turno, null);
            } else if (turno != null) {
                eventoPublisher.publicar(TipoEventoTurno.PAGO_ACREDITADO, turno, null);
            }
            repository.actualizar(p);
            LOG.info("[Pagos] intento #" + p.getId() + " del turno #" + p.getTurnoId() + " conciliado: " + p.getEstado());
            return p.getEstado();
        }
        if (p.getCreadoEn().isBefore(LocalDateTime.now().minusSeconds(ventanaSegundos()))) {
            p.setEstado(Pago.Estado.FALLIDO);
            p.setMotivo("La pasarela no confirmó el cobro");
            repository.actualizar(p);
            LOG.info("[Pagos] intento #" + p.getId() + " sin cobro en la pasarela: FALLIDO");
        }
        return p.getEstado();
    }
}
