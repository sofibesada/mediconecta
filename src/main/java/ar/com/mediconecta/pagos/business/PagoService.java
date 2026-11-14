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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Componente de pagos: cobra con tarjeta lo que le toca pagar al paciente por un turno
 * confirmado (el valor de la consulta si es particular, o el copago si tiene obra social).
 *
 * 1. Valida el turno (confirmado, con importe y sin un pago aprobado).
 * 2. Valida la tarjeta (marca, Luhn, vencimiento, CVV) antes de mandarla.
 * 3. Cobra en la pasarela externa por REST (Adapter: PasarelaPagosClient).
 * 4. Guarda el intento (aprobado o rechazado) sin el numero completo ni el CVV.
 * 5. Si se aprobo, publica PAGO_ACREDITADO en el topico de eventos (aviso al paciente).
 */
@Stateless
public class PagoService {

    @Inject
    private PagoRepository repository;

    @Inject
    private PasarelaPagosClient pasarela;

    @Inject
    private ITurnoService turnoService;

    @Inject
    private TurnoEventoPublisher eventoPublisher;

    /** Datos de la tarjeta tal como los ingresa el paciente. */
    public record DatosTarjeta(String numero, String vencimiento, String cvv, String titular) {}

    @RolesAllowed(Roles.PACIENTE)
    public Pago pagar(Long pacienteId, Long turnoId, DatosTarjeta tarjeta, String claveIdempotencia) {
        Turno turno = turnoService.buscarTurno(turnoId);
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

        String clave = claveIdempotencia != null && !claveIdempotencia.isBlank()
                ? claveIdempotencia : "turno-" + turnoId + "-" + UUID.randomUUID();
        PasarelaPagosClient.Resultado r = pasarela.cobrar(importe, "turno-" + turnoId,
                Tarjetas.soloDigitos(tarjeta.numero()), tarjeta.vencimiento(), tarjeta.cvv(), tarjeta.titular().trim(), clave);

        Pago pago = new Pago();
        pago.setTurnoId(turnoId);
        pago.setPacienteId(pacienteId);
        pago.setImporte(importe);
        pago.setEstado(r.aprobado() ? Pago.Estado.APROBADO : Pago.Estado.RECHAZADO);
        pago.setMarca(r.marca());
        pago.setUltimos4(r.ultimos4());
        pago.setIdExterno(r.id());
        pago.setCodigoAutorizacion(r.codigoAutorizacion());
        pago.setMotivo(r.motivo());
        pago.setCreadoEn(LocalDateTime.now());
        repository.guardar(pago);

        if (!r.aprobado()) {
            throw new PagoRechazadoException(r.motivo()); // rollback = false: el intento queda registrado
        }
        eventoPublisher.publicar(TipoEventoTurno.PAGO_ACREDITADO, turno, null);
        return pago;
    }

    /** Intentos de pago de un turno, del mas nuevo al mas viejo. */
    @PermitAll
    public List<Pago> deTurno(Long turnoId) {
        return repository.listarDeTurno(turnoId);
    }

    /** Pago aprobado de cada turno (los que no estan pagados no aparecen). */
    @PermitAll
    public Map<Long, Pago> aprobadosDe(Collection<Long> turnoIds) {
        return repository.aprobadosDe(turnoIds).stream()
                .collect(Collectors.toMap(Pago::getTurnoId, Function.identity(), (a, b) -> a));
    }

    @RolesAllowed(Roles.ADMIN)
    public List<Pago> listarRecientes() {
        return repository.listarRecientes(50);
    }
}
