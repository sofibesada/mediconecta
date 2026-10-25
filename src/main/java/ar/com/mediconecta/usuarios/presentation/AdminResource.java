package ar.com.mediconecta.usuarios.presentation;

import ar.com.mediconecta.facturacion.business.IFacturacionService;
import ar.com.mediconecta.obrassociales.business.AuditoriaAutorizacionService;
import ar.com.mediconecta.seguridad.UsuarioActual;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import ar.com.mediconecta.usuarios.model.Usuario;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Operaciones de administracion. Todo /api/admin/* exige rol ADMIN en web.xml, y cada
 * operacion de UsuarioService lo vuelve a exigir con @RolesAllowed. El id del admin
 * sale del token, no del cuerpo del pedido.
 */
@Path("/admin")
public class AdminResource {

    @Inject
    private IUsuarioService usuarioService;

    @Inject
    private UsuarioActual usuarioActual;

    @Inject
    private AuditoriaAutorizacionService auditoria;

    @Inject
    private IFacturacionService facturacionService;

    /** Autorizaciones pedidas a las obras sociales (auditadas) y reclamos de facturacion. */
    @GET
    @Path("/facturacion")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> facturacion() {
        return Map.of(
                "autorizaciones", auditoria.listarRecientes(),
                "reclamos", facturacionService.listarRecientes());
    }

    @POST
    @Path("/profesionales")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UsuarioResponse crearProfesional(CrearProfesionalRequest req) {
        Usuario datos = new Usuario();
        datos.setNombre(req.nombre);
        datos.setEmail(req.email);
        datos.setPassword(req.passwordTemporal);
        return UsuarioResponse.from(usuarioService.crearProfesional(usuarioActual.id(), datos));
    }

    @GET
    @Path("/usuarios")
    @Produces(MediaType.APPLICATION_JSON)
    public List<UsuarioResponse> listarUsuarios() {
        return usuarioService.listarTodos().stream()
                .map(UsuarioResponse::from)
                .collect(Collectors.toList());
    }

    @POST
    @Path("/usuarios/{id}/activo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UsuarioResponse cambiarEstado(@PathParam("id") Long id, CambiarEstadoRequest req) {
        return UsuarioResponse.from(usuarioService.cambiarEstadoUsuario(usuarioActual.id(), id, req.activo));
    }

    @POST
    @Path("/usuarios/{id}/reset-password")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> resetPassword(@PathParam("id") Long id) {
        String temporal = usuarioService.resetearPassword(usuarioActual.id(), id);
        return Map.of("passwordTemporal", temporal);
    }
}
