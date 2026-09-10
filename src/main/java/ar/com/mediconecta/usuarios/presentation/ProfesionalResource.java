package ar.com.mediconecta.usuarios.presentation;

import ar.com.mediconecta.usuarios.business.IUsuarioService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Listado publico de profesionales (usuarios con rol PROFESIONAL), para que el
 * frontend ofrezca un desplegable por nombre en vez de pedir un id.
 *
 * Va en su propio path (/profesionales) y no bajo /usuarios/* para no quedar
 * detras del security-constraint de admin del web.xml.
 */
@Path("/profesionales")
public class ProfesionalResource {

    @Inject
    private IUsuarioService usuarioService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<UsuarioResponse> listar() {
        return usuarioService.listarProfesionales().stream()
                .map(UsuarioResponse::from)
                .collect(Collectors.toList());
    }
}
