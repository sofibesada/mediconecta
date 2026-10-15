package ar.com.mediconecta.turnos.presentation;

import ar.com.mediconecta.seguridad.Roles;
import ar.com.mediconecta.seguridad.UsuarioActual;
import ar.com.mediconecta.turnos.business.AgendaTurnoFacade;
import ar.com.mediconecta.turnos.business.ITurnoService;
import ar.com.mediconecta.turnos.model.Turno;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
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

    private TurnoResponse aResponse(Turno t) {
        return TurnoResponse.from(t,
                usuarioService.nombreDe(t.getProfesionalId()),
                usuarioService.nombreDe(t.getPacienteId()));
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
        return turnos.stream()
                .map(t -> TurnoResponse.from(t, nombres.get(t.getProfesionalId()), nombres.get(t.getPacienteId())))
                .collect(Collectors.toList());
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

    @POST
    @Path("/{turnoId}/reservar")
    @Produces(MediaType.APPLICATION_JSON)
    public TurnoResponse reservar(@PathParam("turnoId") Long turnoId) {
        return aResponse(turnoService.reservarTemporalmente(usuarioActual.id(), turnoId));
    }

    @POST
    @Path("/{turnoId}/confirmar")
    @Produces(MediaType.APPLICATION_JSON)
    public TurnoResponse confirmar(@PathParam("turnoId") Long turnoId) {
        Turno turno = turnoService.buscarTurno(turnoId);
        usuarioActual.exigir(turno == null || usuarioActual.esElMismo(turno.getPacienteId()),
                "Solo podés confirmar un turno que reservaste vos");
        return aResponse(turnoService.confirmarTurno(turnoId));
    }

    @PUT
    @Path("/{turnoId}/reprogramar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public TurnoResponse reprogramar(@PathParam("turnoId") Long turnoId, ReprogramarTurnoRequest request) {
        return aResponse(turnoService.reprogramarTurno(usuarioActual.id(), turnoId, request.fechaHora));
    }

    @DELETE
    @Path("/{turnoId}")
    public void cancelar(@PathParam("turnoId") Long turnoId) {
        Turno turno = turnoService.buscarTurno(turnoId);
        usuarioActual.exigir(turno == null || usuarioActual.esElMismo(turno.getPacienteId())
                        || usuarioActual.esElMismo(turno.getProfesionalId()) || usuarioActual.es(Roles.ADMIN),
                "Solo podés cancelar tus propios turnos");
        turnoService.cancelarTurno(turnoId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public TurnoResponse crear(CrearTurnoRequest request) {
        return aResponse(turnoService.crearTurnoDisponible(usuarioActual.id(), request.fechaHora, request.modalidad));
    }

    @POST
    @Path("/agendar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public TurnoResponse agendar(AgendarTurnoRequest request) {
        return aResponse(agendaTurnoFacade.agendarTurno(usuarioActual.id(), request.turnoId));
    }

}
