package ar.com.mediconecta.turnos.presentation;

import ar.com.mediconecta.turnos.business.AgendaTurnoFacade;
import ar.com.mediconecta.turnos.business.ITurnoService;
import ar.com.mediconecta.turnos.model.Turno;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.stream.Collectors;

@Path("/turnos")
public class TurnoResource {

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
        return turnos.stream().map(this::aResponse).collect(Collectors.toList());
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
        return aResponse(turnoService.listarTurnosDePaciente(pacienteId));
    }

    @GET
    @Path("/profesional/{profesionalId}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TurnoResponse> listarPorProfesional(@PathParam("profesionalId") Long profesionalId) {
        return aResponse(turnoService.listarTurnosDeProfesional(profesionalId));
    }

    @POST
    @Path("/{turnoId}/reservar")
    @Produces(MediaType.APPLICATION_JSON)
    public TurnoResponse reservar(@PathParam("turnoId") Long turnoId, @QueryParam("pacienteId") Long pacienteId) {
        return aResponse(turnoService.reservarTemporalmente(pacienteId, turnoId));
    }

    @POST
    @Path("/{turnoId}/confirmar")
    @Produces(MediaType.APPLICATION_JSON)
    public TurnoResponse confirmar(@PathParam("turnoId") Long turnoId) {
        return aResponse(turnoService.confirmarTurno(turnoId));
    }

    @DELETE
    @Path("/{turnoId}")
    public void cancelar(@PathParam("turnoId") Long turnoId) {
        turnoService.cancelarTurno(turnoId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public TurnoResponse crear(CrearTurnoRequest request) {
        return aResponse(turnoService.crearTurnoDisponible(request.profesionalId, request.fechaHora));
    }

    @POST
    @Path("/agendar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public TurnoResponse agendar(AgendarTurnoRequest request) {
        return aResponse(agendaTurnoFacade.agendarTurno(request.pacienteId, request.turnoId));
    }

}
