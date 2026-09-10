package ar.com.mediconecta.usuarios.presentation;

import ar.com.mediconecta.usuarios.business.IUsuarioService;
import ar.com.mediconecta.usuarios.model.Usuario;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/usuarios")
public class UsuarioResource {

    @Inject
    private IUsuarioService usuarioService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UsuarioResponse registrar(Usuario usuario) {
        return UsuarioResponse.from(usuarioService.registrarUsuario(usuario));
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public UsuarioResponse buscar(@PathParam("id") Long id) {
        return UsuarioResponse.from(usuarioService.buscarUsuario(id));
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(LoginRequest request) {
        Usuario usuario = usuarioService.autenticar(request.email, request.password);
        if (usuario == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"Email o contraseña incorrectos\"}")
                    .build();
        }
        return Response.ok(UsuarioResponse.from(usuario)).build();
    }

    @POST
    @Path("/cambiar-password")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UsuarioResponse cambiarPassword(CambiarPasswordRequest request) {
        return UsuarioResponse.from(usuarioService.cambiarPassword(
                request.usuarioId, request.passwordActual, request.passwordNueva));
    }
}
