package ar.com.mediconecta.pagos.business;

import ar.com.mediconecta.pagos.data.PagoRepository;
import ar.com.mediconecta.pagos.integracion.PasarelaPagosClient;
import ar.com.mediconecta.pagos.model.Pago;
import ar.com.mediconecta.seguridad.Roles;
import ar.com.mediconecta.turnos.business.ITurnoService;
import ar.com.mediconecta.turnos.eventos.TipoEventoTurno;
import ar.com.mediconecta.turnos.eventos.TurnoEventoPublisher;
import ar.com.mediconecta.turnos.model.EstadoTurno;
import ar.com.mediconecta.turnos.model.Turno;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Pago del copago de un turno con tarjeta, a traves de la pasarela externa (REST).
 *
 * Un turno se cobra UNA sola vez, aunque lleguen pedidos simultaneos, se repitan o se pierda
 * una respuesta:
 *  1. El turno se bloquea (SELECT ... FOR UPDATE) hasta el fin de la transaccion: los pagos
 *     de un mismo turno se procesan de a uno y el segundo ve que ya esta pagado.
 *  2. Conciliacion: antes de cobrar se le pregunta a la pasarela si ya aprobo un pago de este
 *     turno (por ejemplo, la respuesta llego tarde o la transaccion se revirtio por el broker).
 *     Si lo aprobo, se registra ese y no se cobra de nuevo.
 *  3. La Idempotency-Key hacia la pasarela la arma MediConecta (turno + intento + huella de la
 *     tarjeta), nunca la del navegador: la pasarela es compartida por todos los pacientes y
 *     una clave repetida de otro paciente no puede devolver un pago ajeno.
 */
@Stateless
public class PagoService {

    private static final Logger LOG = Logger.getLogger(PagoService.class.getName());

    @Inject
    private PagoRepository repository;

    @Inject
    private PasarelaPagosClient pasarela;

    @Inject
    private ITurnoService turnoService;

    @Inject
    private TurnoEventoPublisher eventoPublisher;

    @Inject
    private IntentosPago intentos;

    /** Datos de la tarjeta tal como los ingresa el paciente. */
    public record DatosTarjeta(String numero, String vencimiento, String cvv, String titular) {}

    @RolesAllowed(Roles.PACIENTE)
    public Pago pagar(Long pacienteId, Long turnoId, DatosTarjeta tarjeta) {
        Turno turno = turnoService.buscarTurnoParaPagar(turnoId);
        if (turno == null || !pacienteId.equals(turno.getPacienteId())) {
            throw new IllegalArgumentException("Turno inexistente");
        }
        if (turno.getEstado() != EstadoTurno.CONFIRMADO) {
            throw new IllegalStateException("Solo se pueden pagar turnos confirmados");
        }
        BigDecimal importe = turno.getImporteCopago();
        if (importe == null || importe.signum() <= 0) {
            throw new IllegalStateException("Este turno no tiene un importe a pagar");
        }
        if (!repository.aprobadosDe(List.of(turnoId)).isEmpty()) {
            throw new IllegalStateException("Este turno ya está pagado");
        }
        if (tarjeta == null) {
            throw new IllegalArgumentException("Faltan los datos de la tarjeta");
        }
        Tarjetas.validar(tarjeta.numero(), tarjeta.vencimiento(), tarjeta.cvv(), tarjeta.titular(), YearMonth.now());

        String referencia = referencia(turnoId);
        // 1) Intentos anteriores con resultado incierto (503, timeout, caida del broker): primero
        //    se averigua como terminaron. Si alguno se aprobo, el turno ya esta pagado.
        List<Long> pendientes = repository.idsPendientesDe(turnoId);
        for (Long id : pendientes) {
            intentos.conciliar(id);
        }
        if (!pendientes.isEmpty()) {
            List<Pago> aprobado = repository.aprobadosDe(List.of(turnoId));
            if (!aprobado.isEmpty()) {
                return aprobado.get(0); // el cobro anterior si se hizo: no se cobra de nuevo
            }
            if (!repository.idsPendientesDe(turnoId).isEmpty()) {
                throw new PagoEnCursoException(); // todavia puede aprobarse: no se arriesga otro cobro
            }
        }
        // 2) Conciliacion por si la pasarela tiene un cobro que MediConecta nunca registro.
        Optional<PasarelaPagosClient.Resultado> yaCobrado = pasarela.aprobadoDe(referencia);
        String numero = Tarjetas.soloDigitos(tarjeta.numero());
        Long intentoId;
        PasarelaPagosClient.Resultado r;
        if (yaCobrado.isPresent()) {
            r = yaCobrado.get();
            intentoId = intentos.registrarPendiente(turnoId, pacienteId, importe, r.marca(), r.ultimos4(), null);
            LOG.info("[Pagos] la pasarela ya tenia aprobado el pago de " + referencia + ": se concilia sin cobrar de nuevo");
        } else {
            // 3) El intento queda PENDIENTE en su propia transaccion ANTES de cobrar: si la
            //    respuesta se pierde, bloquea otro cobro y el conciliador lo resuelve.
            int intento = repository.listarDeTurno(turnoId).size() + 1;
            String clave = claveParaPasarela(turnoId, intento, numero, tarjeta.vencimiento(), importe);
            intentoId = intentos.registrarPendiente(turnoId, pacienteId, importe,
                    Tarjetas.marca(numero), numero.substring(numero.length() - 4), clave);
            try {
                r = pasarela.cobrar(importe, referencia, numero, tarjeta.vencimiento(), tarjeta.cvv(), tarjeta.titular().trim(), clave);
            } catch (PasarelaPagosClient.DatosInvalidosException e) {
                intentos.marcarFallido(intentoId, e.getMessage()); // seguro que no se cobro
                throw e;
            }
        }

        Pago pago = repository.buscarPorId(intentoId);
        pago.setEstado(r.aprobado() ? Pago.Estado.APROBADO : Pago.Estado.RECHAZADO);
        pago.setMarca(r.marca());
        pago.setUltimos4(r.ultimos4());
        pago.setIdExterno(r.id());
        pago.setCodigoAutorizacion(r.codigoAutorizacion());
        pago.setMotivo(r.motivo());
        repository.actualizar(pago);

        if (!r.aprobado()) {
            throw new PagoRechazadoException(r.motivo()); // rollback = false: el intento queda registrado
        }
        eventoPublisher.publicar(TipoEventoTurno.PAGO_ACREDITADO, turno, null);
        return pago;
    }

    /**
     * Devolucion al cancelar un turno pagado (politica: devolucion total; solo se puede
     * cancelar con 24 h de anticipacion, o lo cancela el profesional). La dispara el evento
     * TURNO_CANCELADO del topico (PagosTurnoMDB). Es idempotente: si el mensaje llega dos
     * veces, el pago ya devuelto no se devuelve de nuevo.
     */
    @PermitAll
    public int reembolsarDeTurno(Long turnoId) {
        // Cobros con resultado incierto: si la pasarela los aprobo, la conciliacion los devuelve
        // (el turno ya esta cancelado). Si todavia no se sabe, lo resuelve ConciliacionPagos.
        for (Long id : repository.idsPendientesDe(turnoId)) {
            intentos.conciliar(id);
        }
        List<Pago> aprobados = repository.aprobadosDe(List.of(turnoId));
        for (Pago p : aprobados) {
            pasarela.reembolsar(p.getIdExterno());
            p.setEstado(Pago.Estado.REEMBOLSADO);
            p.setReembolsadoEn(LocalDateTime.now());
            repository.actualizar(p);
        }
        if (!aprobados.isEmpty()) {
            Turno turno = turnoService.buscarTurno(turnoId);
            if (turno != null) {
                eventoPublisher.publicar(TipoEventoTurno.PAGO_REEMBOLSADO, turno, null);
            }
            LOG.info("[Pagos] turno #" + turnoId + " cancelado: " + aprobados.size() + " pago(s) devuelto(s)");
        }
        return aprobados.size();
    }

    /** Intentos de pago de un turno, del mas nuevo al mas viejo. */
    @PermitAll
    public List<Pago> deTurno(Long turnoId) {
        return repository.listarDeTurno(turnoId);
    }

    /** Pago aprobado o devuelto de cada turno (los que no se pagaron no aparecen). */
    @PermitAll
    public Map<Long, Pago> vigentesDe(Collection<Long> turnoIds) {
        return repository.vigentesDe(turnoIds).stream()
                .collect(Collectors.toMap(Pago::getTurnoId, Function.identity(), (a, b) -> a));
    }

    @RolesAllowed(Roles.ADMIN)
    public List<Pago> listarRecientes() {
        return repository.listarRecientes(50);
    }

    static String referencia(Long turnoId) {
        return "turno-" + turnoId;
    }

    /**
     * Clave de idempotencia hacia la pasarela: la misma para el mismo intento con la misma
     * tarjeta (un reintento tras un timeout no cobra dos veces) y distinta para otro turno,
     * otro intento (despues de un rechazo) u otra tarjeta. No lleva la tarjeta en claro.
     */
    static String claveParaPasarela(Long turnoId, int intento, String numero, String vencimiento, BigDecimal importe) {
        String datos = numero + "|" + vencimiento + "|" + importe.stripTrailingZeros().toPlainString();
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(datos.getBytes(StandardCharsets.UTF_8));
            return "mc-" + referencia(turnoId) + "-" + intento + "-" + HexFormat.of().formatHex(h).substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
