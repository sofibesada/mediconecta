package ar.com.mediconecta.usuarios.presentation;

import ar.com.mediconecta.usuarios.business.Cartilla;
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

    /** obrasSociales: las que atiende; null = todas, vacia = solo particular. */
    public record ProfesionalPublico(Long id, String nombre, String especialidad, List<String> obrasSociales) {}

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProfesionalPublico> listar() {
        return usuarioService.listarProfesionales().stream()
                .map(u -> new ProfesionalPublico(u.getId(), u.getNombre(),
                        u.getEspecialidad() != null ? u.getEspecialidad() : Especialidades.POR_DEFECTO,
                        Cartilla.lista(u.getCartillaObrasSociales())))
                .toList();
    }

    @GET
    @Path("/especialidades")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> especialidades() {
        return Especialidades.LISTA;
    }
}
