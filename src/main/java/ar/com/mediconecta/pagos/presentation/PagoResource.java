package ar.com.mediconecta.pagos.presentation;

import ar.com.mediconecta.idempotencia.business.IdempotenciaService;
import ar.com.mediconecta.pagos.business.PagoService;
import ar.com.mediconecta.pagos.model.Pago;
import ar.com.mediconecta.seguridad.Roles;
import ar.com.mediconecta.seguridad.UsuarioActual;
import ar.com.mediconecta.turnos.business.ITurnoService;
import ar.com.mediconecta.turnos.model.Turno;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pago de turnos con tarjeta.
 *
 *   POST /pagos                 paga un turno (acepta Idempotency-Key: un reintento no cobra dos veces)
 *   GET  /pagos/turno/{turnoId} intentos de pago de un turno
 *
 * Solo el paciente del turno puede pagarlo; un turno ajeno responde 404.
 */
@Path("/pagos")
@Produces(MediaType.APPLICATION_JSON)
public class PagoResource {

    @Inject
    private UsuarioActual usuarioActual;

    @Inject
    private PagoService pagoService;

    @Inject
    private ITurnoService turnoService;

    @Inject
    private IdempotenciaService idempotencia;

    public record PagoRequest(Long turnoId, String numero, String vencimiento, String cvv, String titular) {}

    /** Lo que se devuelve de un pago: nunca el numero completo de la tarjeta. */
    public record PagoResponse(Long id, Long turnoId, String estado, BigDecimal importe, String marca, String ultimos4,
                               String codigoAutorizacion, String motivo, LocalDateTime creadoEn) {
        static PagoResponse from(Pago p) {
            return new PagoResponse(p.getId(), p.getTurnoId(), p.getEstado().name(), p.getImporte(), p.getMarca(),
                    p.getUltimos4(), p.getCodigoAutorizacion(), p.getMotivo(), p.getCreadoEn());
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response pagar(PagoRequest req, @HeaderParam("Idempotency-Key") String clave) {
        if (req == null || req.turnoId() == null) {
            throw new IllegalArgumentException("Falta el turno a pagar");
        }
        Turno turno = turnoService.buscarTurno(req.turnoId());
        usuarioActual.exigir(turno != null && usuarioActual.esElMismo(turno.getPacienteId()),
                "Solo podés pagar tus propios turnos");
        Long pacienteId = usuarioActual.id();
        PagoService.DatosTarjeta tarjeta = new PagoService.DatosTarjeta(req.numero(), req.vencimiento(), req.cvv(), req.titular());
        IdempotenciaService.Resultado r = idempotencia.ejecutar(clave, pacienteId, "POST /pagos turno " + req.turnoId(),
                () -> PagoResponse.from(pagoService.pagar(pacienteId, req.turnoId(), tarjeta, clave)));
        if (r.repetido()) {
            return Response.ok(r.json(), MediaType.APPLICATION_JSON).header("Idempotent-Replayed", "true").build();
        }
        return Response.status(Response.Status.CREATED).entity(r.cuerpo()).build();
    }

    @GET
    @Path("/turno/{turnoId}")
    public List<PagoResponse> deTurno(@PathParam("turnoId") Long turnoId) {
        Turno turno = turnoService.buscarTurno(turnoId);
        usuarioActual.exigir(turno != null && (usuarioActual.esElMismo(turno.getPacienteId())
                        || usuarioActual.esElMismo(turno.getProfesionalId()) || usuarioActual.es(Roles.ADMIN)),
                "Solo podés ver los pagos de tus turnos");
        return pagoService.deTurno(turnoId).stream().map(PagoResponse::from).toList();
    }
}
