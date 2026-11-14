package ar.com.mediconecta.usuarios.presentation;

import ar.com.mediconecta.config.ApiError;
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
    private ar.com.mediconecta.usuarios.business.RecuperacionPasswordService recuperacion;

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
            return ApiError.respuesta(Response.Status.UNAUTHORIZED, "Email o contraseña incorrectos");
        }
        if (!usuario.isActivo()) {
            return ApiError.respuesta(Response.Status.FORBIDDEN, "Tu cuenta está desactivada. Contactá al administrador.");
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

    /**
     * Devuelve un token nuevo para el usuario del token actual (que todavia tiene que ser
     * valido). Asi la sesion dura mientras se use, aunque cada token venza a los 15 min.
     */
    /**
     * "Olvide mi contrasena": le avisa al administrador. Responde siempre lo mismo, exista o no
     * la cuenta, para no revelar que emails estan registrados.
     */
    @POST
    @Path("/recuperar-password")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response recuperarPassword(RecuperarRequest request) {
        recuperacion.solicitarPorEmail(request == null ? null : request.email());
        return Response.accepted(java.util.Map.of("mensaje",
                "Si el email corresponde a una cuenta activa, le avisamos al administrador para que te genere una contraseña temporal.")).build();
    }

    public record RecuperarRequest(String email) {}

    @POST
    @Path("/renovar")
    @Produces(MediaType.APPLICATION_JSON)
    public RenovacionResponse renovar() {
        return new RenovacionResponse(tokenService.emitir(usuarioActual.id()));
    }

    public record RenovacionResponse(String token) {}

    private UsuarioResponse conToken(Usuario usuario) {
        UsuarioResponse r = UsuarioResponse.from(usuario);
        r.token = tokenService.emitir(usuario.getId());
        return r;
    }
}
