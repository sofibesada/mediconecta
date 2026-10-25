package ar.com.mediconecta.historiaclinica.presentation;

import ar.com.mediconecta.historiaclinica.business.IHistoriaClinicaService;
import ar.com.mediconecta.historiaclinica.model.Diagnostico;
import ar.com.mediconecta.historiaclinica.model.HistoriaClinica;
import ar.com.mediconecta.historiaclinica.model.Receta;
import ar.com.mediconecta.obrassociales.business.IObraSocialService;
import ar.com.mediconecta.obrassociales.business.IObraSocialService.CoberturaMedicamento;
import ar.com.mediconecta.obrassociales.integracion.ObrasSocialesNoDisponibleException;
import ar.com.mediconecta.seguridad.Roles;
import ar.com.mediconecta.seguridad.UsuarioActual;
import ar.com.mediconecta.turnos.business.ITurnoService;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import ar.com.mediconecta.usuarios.model.Usuario;
import jakarta.ejb.EJBException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

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

    @Inject
    private IUsuarioService usuarioService;

    @Inject
    private IObraSocialService obraSocialService;

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

    /**
     * Ficha del paciente: sus datos, su cobertura y, por cada receta, si tiene descuento
     * con su obra social. El descuento se consulta en el momento al sistema legado por
     * SOAP (consultarCoberturaMedicamentos). Si el legado no responde, la ficha se
     * devuelve igual, con un aviso y sin el detalle de descuentos.
     */
    @GET
    @Path("/{pacienteId}/ficha")
    @Produces(MediaType.APPLICATION_JSON)
    public FichaPacienteResponse ficha(@PathParam("pacienteId") Long pacienteId) {
        exigirAcceso(pacienteId);
        Usuario paciente = usuarioService.buscarParaSesion(pacienteId);
        if (paciente == null) {
            throw new NotFoundException("Paciente no encontrado");
        }
        List<Receta> recetas = historiaClinicaService.consultarHistoria(pacienteId).getRecetas();

        FichaPacienteResponse f = new FichaPacienteResponse();
        f.id = paciente.getId();
        f.nombre = paciente.getNombre();
        f.email = paciente.getEmail();
        f.obraSocial = paciente.getObraSocial();
        f.planObraSocial = paciente.getPlanObraSocial();
        f.numeroAfiliado = paciente.getNumeroAfiliado();
        f.copagoPorcentaje = paciente.getCopagoPorcentaje();

        List<CoberturaMedicamento> coberturas = List.of();
        if (paciente.tieneObraSocial() && !recetas.isEmpty()) {
            try {
                coberturas = obraSocialService.coberturaMedicamentos(paciente.getObraSocial(), paciente.getNumeroAfiliado(),
                        recetas.stream().map(Receta::getMedicamento).toList());
            } catch (EJBException e) {
                if (!(e.getCause() instanceof ObrasSocialesNoDisponibleException)) {
                    throw e;
                }
                f.avisoCobertura = "El sistema de la obra social no responde: no se pudo consultar qué recetas tienen descuento.";
            }
        }
        f.recetas = new ArrayList<>();
        for (int i = 0; i < recetas.size(); i++) {
            FichaPacienteResponse.RecetaCobertura rc = new FichaPacienteResponse.RecetaCobertura();
            rc.recetaId = recetas.get(i).getId();
            rc.medicamento = recetas.get(i).getMedicamento();
            if (i < coberturas.size()) {
                rc.cubierto = coberturas.get(i).cubierto();
                rc.descuentoPorcentaje = coberturas.get(i).descuentoPorcentaje();
            }
            f.recetas.add(rc);
        }
        return f;
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