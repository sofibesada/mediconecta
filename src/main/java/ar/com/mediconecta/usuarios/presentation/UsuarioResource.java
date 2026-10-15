package ar.com.mediconecta.usuarios.presentation;

import ar.com.mediconecta.seguridad.TokenService;
import ar.com.mediconecta.seguridad.UsuarioActual;
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

    @Inject
    private TokenService tokenService;

    @Inject
    private UsuarioActual usuarioActual;

    /** Registro publico (solo PACIENTE). Devuelve el token: la sesion queda iniciada. */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UsuarioResponse registrar(Usuario usuario) {
        return conToken(usuarioService.registrarUsuario(usuario));
    }

    /** Protegido con rol ADMIN en web.xml y con @RolesAllowed en UsuarioService.buscarUsuario. */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public UsuarioResponse buscar(@PathParam("id") Long id) {
        Usuario usuario = usuarioService.buscarUsuario(id);
        if (usuario == null) {
            throw new NotFoundException("Usuario no encontrado");
        }
        return UsuarioResponse.from(usuario);
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
        if (!usuario.isActivo()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"Tu cuenta está desactivada. Contactá al administrador.\"}")
                    .build();
        }
        return Response.ok(conToken(usuario)).build();
    }

    /**
     * Cualquier usuario autenticado, aunque todavia no tenga rol por tener la contrasena
     * temporal (web.xml: role-name "**"). Solo puede cambiar SU contrasena.
     */
    @POST
    @Path("/cambiar-password")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UsuarioResponse cambiarPassword(CambiarPasswordRequest request) {
        return conToken(usuarioService.cambiarPassword(
                usuarioActual.id(), request.passwordActual, request.passwordNueva));
    }

    private UsuarioResponse conToken(Usuario usuario) {
        UsuarioResponse r = UsuarioResponse.from(usuario);
        r.token = tokenService.emitir(usuario.getId());
        return r;
    }
}
