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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Intentos de pago con resultado incierto (la pasarela no respondio a tiempo, o MediConecta
 * no pudo guardar la respuesta). Cada metodo va en su PROPIA transaccion (REQUIRES_NEW): el
 * intento PENDIENTE queda registrado aunque el cobro termine en error, y la conciliacion de
 * un pago no depende de la transaccion de quien la pide.
 *
 * Conciliar = preguntarle a la pasarela como termino ese cobro:
 *  - aprobado: se registra; si el turno ya se cancelo, se devuelve en el momento;
 *  - sin resultado todavia: sigue PENDIENTE y bloquea otro cobro del turno;
 *  - sin resultado despues de la ventana: se ANULA ese intento en la pasarela por su clave. Solo
 *    con la respuesta definitiva (ANULADO o RECHAZADO) queda FALLIDO y se puede pagar de nuevo;
 *    si la pasarela responde APROBADO (el pedido llego), se registra esa aprobacion. El tiempo
 *    solo decide CUANDO preguntar, nunca el resultado: un pedido que llega tarde no cobra.
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
    public Long registrarPendiente(Long turnoId, Long pacienteId, BigDecimal importe, String marca, String ultimos4,
                                   String claveExterna) {
        Pago p = new Pago();
        p.setTurnoId(turnoId);
        p.setPacienteId(pacienteId);
        p.setImporte(importe);
        p.setEstado(Pago.Estado.PENDIENTE);
        p.setMarca(marca);
        p.setUltimos4(ultimos4);
        p.setClaveExterna(claveExterna);
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
            return registrarAprobacion(p, aprobado.get());
        }
        if (p.getCreadoEn().isAfter(LocalDateTime.now().minusSeconds(ventanaSegundos()))) {
            return p.getEstado(); // el pedido puede seguir en camino: sigue PENDIENTE y bloqueando
        }
        if (p.getClaveExterna() == null) {
            // Intento de una version anterior, sin clave guardada: no hay como anularlo.
            p.setEstado(Pago.Estado.FALLIDO);
            p.setMotivo("La pasarela no confirmó el cobro");
            repository.actualizar(p);
            return p.getEstado();
        }
        // Respuesta definitiva: se anula el intento en la pasarela. Si el pedido llega despues, no cobra.
        PasarelaPagosClient.Resultado r = pasarela.anular(p.getClaveExterna(), PagoService.referencia(p.getTurnoId()));
        if (r.aprobado()) {
            return registrarAprobacion(p, r); // el pedido habia llegado y se aprobo
        }
        p.setEstado(Pago.Estado.FALLIDO);
        p.setIdExterno(r.id());
        p.setMotivo("ANULADO".equals(r.estado()) ? "Intento anulado en la pasarela: no se cobró" : r.motivo());
        repository.actualizar(p);
        LOG.info("[Pagos] intento #" + p.getId() + " cerrado en la pasarela como " + r.estado() + ": FALLIDO");
        return p.getEstado();
    }

    /**
     * Red de seguridad contra el cobro doble (D-11): si el turno ya tiene su pago registrado y la
     * pasarela tiene OTRO cobro aprobado de ese turno que MediConecta no conoce (por ejemplo, el
     * pedido de un intento de la version anterior, sin clave para anularlo, que llego tarde), ese
     * cobro se devuelve y queda registrado como REEMBOLSADO. Devuelve cuantos devolvio.
     *
     * No se toca un turno con intentos PENDIENTES (un cobro en curso todavia no esta registrado)
     * ni uno sin pago registrado (el primer cobro puede estar terminando de guardarse).
     */
    @PermitAll
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int devolverDuplicados(Long turnoId) {
        if (!repository.idsPendientesDe(turnoId).isEmpty()) {
            return 0;
        }
        List<Pago> registrados = repository.vigentesDe(List.of(turnoId));
        if (registrados.isEmpty()) {
            return 0;
        }
        Set<String> conocidos = repository.idsExternosDe(turnoId);
        Pago base = registrados.get(0);
        int devueltos = 0;
        for (PasarelaPagosClient.Resultado r : pasarela.aprobadosDe(PagoService.referencia(turnoId))) {
            if (conocidos.contains(r.id())) {
                continue;
            }
            pasarela.reembolsar(r.id());
            Pago duplicado = new Pago();
            duplicado.setTurnoId(turnoId);
            duplicado.setPacienteId(base.getPacienteId());
            duplicado.setImporte(base.getImporte());
            duplicado.setEstado(Pago.Estado.REEMBOLSADO);
            duplicado.setMarca(r.marca());
            duplicado.setUltimos4(r.ultimos4());
            duplicado.setIdExterno(r.id());
            duplicado.setCodigoAutorizacion(r.codigoAutorizacion());
            duplicado.setMotivo("Cobro duplicado en la pasarela: devuelto automáticamente");
            duplicado.setCreadoEn(LocalDateTime.now());
            duplicado.setReembolsadoEn(LocalDateTime.now());
            repository.guardar(duplicado);
            devueltos++;
            LOG.warning("[Pagos] cobro duplicado " + r.id() + " del turno #" + turnoId + ": devuelto");
        }
        if (devueltos > 0) {
            Turno turno = turnoService.buscarTurno(turnoId);
            if (turno != null) {
                eventoPublisher.publicar(TipoEventoTurno.PAGO_REEMBOLSADO, turno, null);
            }
        }
        return devueltos;
    }

    private Pago.Estado registrarAprobacion(Pago p, PasarelaPagosClient.Resultado r) {
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
}
