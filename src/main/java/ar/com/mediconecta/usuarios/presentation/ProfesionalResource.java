package ar.com.mediconecta.usuarios.presentation;

import ar.com.mediconecta.usuarios.business.Especialidades;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * Listado PUBLICO de profesionales (el paciente elige con quien atenderse). Solo expone el
 * nombre y la especialidad: nunca el email ni otros datos de la cuenta.
 */
@Path("/profesionales")
public class ProfesionalResource {

    @Inject
    private IUsuarioService usuarioService;

    public record ProfesionalPublico(Long id, String nombre, String especialidad) {}

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProfesionalPublico> listar() {
        return usuarioService.listarProfesionales().stream()
                .map(u -> new ProfesionalPublico(u.getId(), u.getNombre(),
                        u.getEspecialidad() != null ? u.getEspecialidad() : Especialidades.POR_DEFECTO))
                .toList();
    }

    @GET
    @Path("/especialidades")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> especialidades() {
        return Especialidades.LISTA;
    }
}
