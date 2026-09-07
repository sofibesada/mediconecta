package ar.com.mediconecta.historiaclinica.presentation;

import ar.com.mediconecta.historiaclinica.business.IHistoriaClinicaService;
import ar.com.mediconecta.historiaclinica.model.Diagnostico;
import ar.com.mediconecta.historiaclinica.model.HistoriaClinica;
import ar.com.mediconecta.historiaclinica.model.Receta;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/historia-clinica")
public class HistoriaClinicaResource {

    @Inject
    private IHistoriaClinicaService historiaClinicaService;

    @GET
    @Path("/{pacienteId}")
    @Produces(MediaType.APPLICATION_JSON)
    public HistoriaClinica consultar(@PathParam("pacienteId") Long pacienteId) {
        return historiaClinicaService.consultarHistoria(pacienteId);
    }

    @POST
    @Path("/{pacienteId}/diagnosticos")
    @Consumes(MediaType.APPLICATION_JSON)
    public void registrarDiagnostico(@PathParam("pacienteId") Long pacienteId, Diagnostico diagnostico) {
        historiaClinicaService.registrarDiagnostico(pacienteId, diagnostico);
    }

    @POST
    @Path("/{pacienteId}/recetas")
    @Consumes(MediaType.APPLICATION_JSON)
    public void registrarReceta(@PathParam("pacienteId") Long pacienteId, Receta receta) {
        historiaClinicaService.registrarReceta(pacienteId, receta);
    }
}