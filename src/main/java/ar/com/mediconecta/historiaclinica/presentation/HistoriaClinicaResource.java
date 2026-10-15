package ar.com.mediconecta.historiaclinica.presentation;

import ar.com.mediconecta.historiaclinica.business.IHistoriaClinicaService;
import ar.com.mediconecta.historiaclinica.model.Diagnostico;
import ar.com.mediconecta.historiaclinica.model.HistoriaClinica;
import ar.com.mediconecta.historiaclinica.model.Receta;
import ar.com.mediconecta.seguridad.Roles;
import ar.com.mediconecta.seguridad.UsuarioActual;
import ar.com.mediconecta.turnos.business.ITurnoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * Datos clinicos: el paciente solo accede a SU historia y el profesional solo a la de
 * pacientes que tienen (o tuvieron) un turno con el. El rol lo exige el EJB.
 */
@Path("/historia-clinica")
public class HistoriaClinicaResource {

    @Inject
    private IHistoriaClinicaService historiaClinicaService;

    @Inject
    private ITurnoService turnoService;

    @Inject
    private UsuarioActual usuarioActual;

    private void exigirAcceso(Long pacienteId) {
        if (usuarioActual.es(Roles.PROFESIONAL)) {
            Long profesionalId = usuarioActual.id();
            boolean esSuPaciente = turnoService.listarTurnosDeProfesional(profesionalId).stream()
                    .anyMatch(t -> pacienteId.equals(t.getPacienteId()));
            usuarioActual.exigir(esSuPaciente, "Solo podés acceder a la historia clínica de tus pacientes");
        } else {
            usuarioActual.exigir(usuarioActual.esElMismo(pacienteId), "Solo podés acceder a tu propia historia clínica");
        }
    }

    @GET
    @Path("/{pacienteId}")
    @Produces(MediaType.APPLICATION_JSON)
    public HistoriaClinica consultar(@PathParam("pacienteId") Long pacienteId) {
        exigirAcceso(pacienteId);
        return historiaClinicaService.consultarHistoria(pacienteId);
    }

    @POST
    @Path("/{pacienteId}/diagnosticos")
    @Consumes(MediaType.APPLICATION_JSON)
    public void registrarDiagnostico(@PathParam("pacienteId") Long pacienteId, Diagnostico diagnostico) {
        exigirAcceso(pacienteId);
        historiaClinicaService.registrarDiagnostico(pacienteId, diagnostico);
    }

    @POST
    @Path("/{pacienteId}/recetas")
    @Consumes(MediaType.APPLICATION_JSON)
    public void registrarReceta(@PathParam("pacienteId") Long pacienteId, Receta receta) {
        exigirAcceso(pacienteId);
        historiaClinicaService.registrarReceta(pacienteId, receta);
    }
}