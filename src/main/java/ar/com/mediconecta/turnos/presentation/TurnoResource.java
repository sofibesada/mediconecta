package ar.com.mediconecta.turnos.presentation;

import ar.com.mediconecta.idempotencia.business.IdempotenciaService;
import ar.com.mediconecta.pagos.business.PagoService;
import ar.com.mediconecta.pagos.model.Pago;
import ar.com.mediconecta.seguridad.Roles;
import ar.com.mediconecta.seguridad.UsuarioActual;
import ar.com.mediconecta.turnos.business.AgendaTurnoFacade;
import ar.com.mediconecta.turnos.business.ITurnoService;
import ar.com.mediconecta.turnos.model.Turno;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turnos. El rol lo exige TurnoService con @RolesAllowed; aca se controla que el turno
 * sea DEL usuario del token (un paciente no puede ver ni tocar turnos de otro).
 */
@Path("/turnos")
public class TurnoResource {

    @Inject
    private UsuarioActual usuarioActual;

    @Inject
    private ITurnoService turnoService;

    @Inject
    private AgendaTurnoFacade agendaTurnoFacade;

    @Inject
    private IUsuarioService usuarioService;

    @Inject
    private IdempotenciaService idempotencia;

    @Inject
    private PagoService pagoService;

    private TurnoResponse aResponse(Turno t) {
        return aResponse(List.of(t)).get(0);
    }

    /** Especialidad del profesional y pago online de cada turno, con una consulta para toda la lista. */
    private void completar(List<TurnoResponse> respuestas) {
        Set<Long> profesionales = respuestas.stream().map(r -> r.profesionalId).collect(Collectors.toSet());
        Map<Long, String> especialidades = usuarioService.especialidadesDe(profesionales);
        Map<Long, Pago> pagos = pagoService.aprobadosDe(respuestas.stream().map(r -> r.id).toList());
        for (TurnoResponse r : respuestas) {
            r.profesionalEspecialidad = especialidades.get(r.profesionalId);
            Pago p = pagos.get(r.id);
            if (p != null) {
                r.pago = new TurnoResponse.PagoTurno(p.getMarca(), p.getUltimos4(), p.getCodigoAutorizacion(), p.getCreadoEn());
            }
        }
    }

    /** Respuesta con el turno y su ETag (la version actual). */
    private Response conEtag(Turno t) {
        return Response.ok(aResponse(t)).tag(EtagDeTurno.de(t)).build();
    }

    private List<TurnoResponse> aResponse(List<Turno> turnos) {
        // Una sola consulta para todos los nombres de la lista, en vez de
        // 1-2 por turno (evita N+1 al listar disponibilidad/mis turnos).
        Set<Long> ids = new HashSet<>();
        for (Turno t : turnos) {
            ids.add(t.getProfesionalId());
            if (t.getPacienteId() != null) {
                ids.add(t.getPacienteId());
            }
        }
        Map<Long, String> nombres = usuarioService.nombresDe(ids);
        List<TurnoResponse> respuestas = turnos.stream()
                .map(t -> TurnoResponse.from(t, nombres.get(t.getProfesionalId()), nombres.get(t.getPacienteId())))
                .collect(Collectors.toList());
        completar(respuestas);
        return respuestas;
    }

    @GET
    @Path("/disponibilidad/{profesionalId}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TurnoResponse> consultarDisponibilidad(@PathParam("profesionalId") Long profesionalId) {
        return aResponse(turnoService.consultarDisponibilidad(profesionalId));
    }

    @GET
    @Path("/paciente/{pacienteId}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TurnoResponse> listarPorPaciente(@PathParam("pacienteId") Long pacienteId) {
        usuarioActual.exigir(usuarioActual.esElMismo(pacienteId) || usuarioActual.es(Roles.ADMIN),
                "Solo podés ver tus propios turnos");
        return aResponse(turnoService.listarTurnosDePaciente(pacienteId));
    }

    @GET
    @Path("/profesional/{profesionalId}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TurnoResponse> listarPorProfesional(@PathParam("profesionalId") Long profesionalId) {
        usuarioActual.exigir(usuarioActual.esElMismo(profesionalId) || usuarioActual.es(Roles.ADMIN),
                "Solo podés ver tu propia agenda");
        return aResponse(turnoService.listarTurnosDeProfesional(profesionalId));
    }

    /**
     * Un turno con su ETag (version). Con If-None-Match igual a la version actual responde
     * 304 sin cuerpo: el cliente ya tiene los datos al dia.
     */
    @GET
    @Path("/{turnoId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response obtener(@PathParam("turnoId") Long turnoId, @HeaderParam("If-None-Match") String ifNoneMatch) {
        Turno turno = turnoService.buscarTurno(turnoId);
        usuarioActual.exigir(turno != null && (usuarioActual.esElMismo(turno.getPacienteId())
                        || usuarioActual.esElMismo(turno.getProfesionalId()) || usuarioActual.es(Roles.ADMIN)),
                "Solo podés ver tus propios turnos");
        if (EtagDeTurno.noCambio(ifNoneMatch, turno)) {
            return Response.notModified(EtagDeTurno.de(turno)).build();
        }
        return conEtag(turno);
    }

    @POST
    @Path("/{turnoId}/reservar")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reservar(@PathParam("turnoId") Long turnoId) {
        return conEtag(turnoService.reservarTemporalmente(usuarioActual.id(), turnoId));
    }

    @POST
    @Path("/{turnoId}/confirmar")
    @Produces(MediaType.APPLICATION_JSON)
    public Response confirmar(@PathParam("turnoId") Long turnoId, @QueryParam("particular") boolean particular,
                              @HeaderParam("Idempotency-Key") String clave) {
        Turno turno = turnoService.buscarTurno(turnoId);
        usuarioActual.exigir(turno == null || usuarioActual.esElMismo(turno.getPacienteId()),
                "Solo podés confirmar un turno que reservaste vos");
        if (clave == null || clave.isBlank()) {
            return conEtag(turnoService.confirmarTurno(turnoId, particular));
        }
        // Con Idempotency-Key: la confirmacion y el registro de la clave van en una sola transaccion.
        IdempotenciaService.Resultado r = idempotencia.ejecutar(clave, usuarioActual.id(),
                "POST /turnos/" + turnoId + "/confirmar?particular=" + particular,
                () -> aResponse(turnoService.confirmarTurno(turnoId, particular)));
        if (r.repetido()) {
            return Response.ok(r.json(), MediaType.APPLICATION_JSON).header("Idempotent-Replayed", "true").build();
        }
        return Response.ok(r.cuerpo()).build();
    }

    @PUT
    @Path("/{turnoId}/reprogramar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response reprogramar(@PathParam("turnoId") Long turnoId, ReprogramarTurnoRequest request,
                                @HeaderParam("If-Match") String ifMatch) {
        EtagDeTurno.exigirIfMatch(ifMatch, turnoService.buscarTurno(turnoId));
        return conEtag(turnoService.reprogramarTurno(usuarioActual.id(), turnoId, request.fechaHora));
    }

    @DELETE
    @Path("/{turnoId}")
    public void cancelar(@PathParam("turnoId") Long turnoId, @HeaderParam("If-Match") String ifMatch) {
        Turno turno = turnoService.buscarTurno(turnoId);
        usuarioActual.exigir(turno == null || usuarioActual.esElMismo(turno.getPacienteId())
                        || usuarioActual.esElMismo(turno.getProfesionalId()) || usuarioActual.es(Roles.ADMIN),
                "Solo podés cancelar tus propios turnos");
        EtagDeTurno.exigirIfMatch(ifMatch, turno);
        turnoService.cancelarTurno(turnoId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response crear(CrearTurnoRequest request) {
        return conEtag(turnoService.crearTurnoDisponible(usuarioActual.id(), request.fechaHora, request.modalidad));
    }

    @POST
    @Path("/agendar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public TurnoResponse agendar(AgendarTurnoRequest request) {
        return aResponse(agendaTurnoFacade.agendarTurno(usuarioActual.id(), request.turnoId));
    }

}
