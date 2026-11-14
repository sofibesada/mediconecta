package ar.com.mediconecta.usuarios.presentation;

import ar.com.mediconecta.seguridad.UsuarioActual;
import ar.com.mediconecta.usuarios.business.DatosPerfil;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import ar.com.mediconecta.usuarios.business.RecuperacionPasswordService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/**
 * La cuenta del usuario con la sesion iniciada. Siempre opera sobre el usuario del token:
 * no recibe ids, asi que nadie puede cambiar los datos de otro.
 */
@Path("/perfil")
@Produces(MediaType.APPLICATION_JSON)
public class PerfilResource {

    @Inject
    private UsuarioActual usuarioActual;

    @Inject
    private IUsuarioService usuarioService;

    @Inject
    private RecuperacionPasswordService recuperacion;

    public record ActualizarPerfilRequest(String nombre, String email, String especialidad, String passwordActual) {}

    @GET
    public UsuarioResponse obtener() {
        return UsuarioResponse.from(usuarioService.buscarParaSesion(usuarioActual.id()));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public UsuarioResponse actualizar(ActualizarPerfilRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Faltan los datos");
        }
        return UsuarioResponse.from(usuarioService.actualizarPerfil(usuarioActual.id(),
                new DatosPerfil(req.nombre(), req.email(), req.especialidad(), req.passwordActual())));
    }

    /** "No recuerdo mi contrasena actual": le avisa al administrador. */
    @POST
    @Path("/recuperar-password")
    public Response recuperarPassword() {
        recuperacion.solicitarDesdePerfil(usuarioActual.id());
        return Response.accepted(Map.of("mensaje",
                "Le avisamos al administrador. Te va a dar una contraseña temporal para que ingreses y la cambies.")).build();
    }
}
