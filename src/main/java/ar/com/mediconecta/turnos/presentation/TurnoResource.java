package ar.com.mediconecta.turnos.presentation;

import ar.com.mediconecta.turnos.business.ITurnoService;
import ar.com.mediconecta.turnos.model.Turno;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/turnos")
public class TurnoResource {

    @Inject
    private ITurnoService turnoService;

    @GET
    @Path("/disponibilidad/{profesionalId}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Turno> consultarDisponibilidad(@PathParam("profesionalId") Long profesionalId) {
        return turnoService.consultarDisponibilidad(profesionalId);
    }

    @POST
    @Path("/{turnoId}/reservar")
    @Produces(MediaType.APPLICATION_JSON)
    public Turno reservar(@PathParam("turnoId") Long turnoId, @QueryParam("pacienteId") Long pacienteId) {
        return turnoService.reservarTemporalmente(pacienteId, turnoId);
    }

    @POST
    @Path("/{turnoId}/confirmar")
    @Produces(MediaType.APPLICATION_JSON)
    public Turno confirmar(@PathParam("turnoId") Long turnoId) {
        return turnoService.confirmarTurno(turnoId);
    }

    @DELETE
    @Path("/{turnoId}")
    public void cancelar(@PathParam("turnoId") Long turnoId) {
        turnoService.cancelarTurno(turnoId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Turno crear(CrearTurnoRequest request) {
        return turnoService.crearTurnoDisponible(request.profesionalId, request.fechaHora);
    }
}