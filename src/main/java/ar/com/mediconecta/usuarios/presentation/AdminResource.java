package ar.com.mediconecta.usuarios.presentation;

import ar.com.mediconecta.usuarios.business.IUsuarioService;
import ar.com.mediconecta.usuarios.model.Usuario;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Operaciones de administracion. Cada endpoint recibe el adminId del solicitante
 * y el servicio valida que ese usuario tenga rol ADMIN.
 *
 * (Sin autenticacion real el backend no puede verificar quien llama; valida el
 * rol del id que se le pasa. La verificacion fuerte seria con JWT.)
 */
@Path("/admin")
public class AdminResource {

    @Inject
    private IUsuarioService usuarioService;

    @POST
    @Path("/profesionales")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UsuarioResponse crearProfesional(CrearProfesionalRequest req) {
        Usuario datos = new Usuario();
        datos.setNombre(req.nombre);
        datos.setEmail(req.email);
        datos.setPassword(req.passwordTemporal);
        return UsuarioResponse.from(usuarioService.crearProfesional(req.adminId, datos));
    }

    @GET
    @Path("/usuarios")
    @Produces(MediaType.APPLICATION_JSON)
    public List<UsuarioResponse> listarUsuarios(@QueryParam("adminId") Long adminId) {
        if (!usuarioService.esAdmin(adminId)) {
            throw new IllegalArgumentException("Solo un administrador puede ver el listado de usuarios");
        }
        return usuarioService.listarTodos().stream()
                .map(UsuarioResponse::from)
                .collect(Collectors.toList());
    }

    @POST
    @Path("/usuarios/{id}/activo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UsuarioResponse cambiarEstado(@PathParam("id") Long id, CambiarEstadoRequest req) {
        return UsuarioResponse.from(usuarioService.cambiarEstadoUsuario(req.adminId, id, req.activo));
    }

    @POST
    @Path("/usuarios/{id}/reset-password")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> resetPassword(@PathParam("id") Long id, ResetPasswordRequest req) {
        String temporal = usuarioService.resetearPassword(req.adminId, id);
        return Map.of("passwordTemporal", temporal);
    }
}
