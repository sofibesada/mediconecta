package ar.com.mediconecta.historiaclinica.presentation;

import ar.com.mediconecta.historiaclinica.business.AuditoriaHistoriaClinicaService;
import ar.com.mediconecta.historiaclinica.business.IHistoriaClinicaService;
import ar.com.mediconecta.historiaclinica.model.AccesoHistoriaClinica.Recurso;
import ar.com.mediconecta.historiaclinica.model.AccesoHistoriaClinica.Resultado;
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
 *
 * Cada consulta (historia o ficha) queda auditada (SEC-09): la autorizada, despues de
 * obtener los datos (si no se puede registrar, no se devuelven); la denegada, como DENEGADO.
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

    @Inject
    private AuditoriaHistoriaClinicaService auditoria;

    private boolean tieneAcceso(Long pacienteId) {
        if (usuarioActual.es(Roles.PROFESIONAL)) {
            Long profesionalId = usuarioActual.id();
            return turnoService.listarTurnosDeProfesional(profesionalId).stream()
                    .anyMatch(t -> pacienteId.equals(t.getPacienteId()));
        }
        return usuarioActual.esElMismo(pacienteId);
    }

    private void exigirAcceso(Long pacienteId) {
        usuarioActual.exigir(tieneAcceso(pacienteId), usuarioActual.es(Roles.PROFESIONAL)
                ? "Solo podés acceder a la historia clínica de tus pacientes"
                : "Solo podés acceder a tu propia historia clínica");
    }

    /** Como exigirAcceso, pero un acceso denegado a una consulta queda auditado. */
    private void exigirConsulta(Long pacienteId, Recurso recurso) {
        if (!tieneAcceso(pacienteId)) {
            auditar(pacienteId, recurso, Resultado.DENEGADO);
        }
        exigirAcceso(pacienteId);
    }

    private void auditar(Long pacienteId, Recurso recurso, Resultado resultado) {
        String rol = usuarioActual.es(Roles.ADMIN) ? Roles.ADMIN
                : usuarioActual.es(Roles.PROFESIONAL) ? Roles.PROFESIONAL : Roles.PACIENTE;
        auditoria.registrar(usuarioActual.id(), rol, pacienteId, recurso, resultado);
    }

    @GET
    @Path("/{pacienteId}")
    @Produces(MediaType.APPLICATION_JSON)
    public HistoriaClinica consultar(@PathParam("pacienteId") Long pacienteId) {
        exigirConsulta(pacienteId, Recurso.HISTORIA_CLINICA);
        HistoriaClinica historia = historiaClinicaService.consultarHistoria(pacienteId);
        auditar(pacienteId, Recurso.HISTORIA_CLINICA, Resultado.AUTORIZADO);
        return historia;
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
        exigirConsulta(pacienteId, Recurso.FICHA_PACIENTE);
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
        auditar(pacienteId, Recurso.FICHA_PACIENTE, Resultado.AUTORIZADO);
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