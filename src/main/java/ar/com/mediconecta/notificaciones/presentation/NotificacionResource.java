package ar.com.mediconecta.notificaciones.presentation;

import ar.com.mediconecta.notificaciones.business.INotificacionService;
import ar.com.mediconecta.seguridad.UsuarioActual;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/notificaciones")
@Produces(MediaType.APPLICATION_JSON)
public class NotificacionResource {

    @Inject
    private INotificacionService notificacionService;

    /** Siempre las del usuario del token. */
    @Inject
    private UsuarioActual usuarioActual;

    @GET
    public List<NotificacionResponse> listar() {
        return notificacionService.listarDeUsuario(usuarioActual.id()).stream()
                .map(NotificacionResponse::from)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/no-leidas")
    public Map<String, Long> contarNoLeidas() {
        return Map.of("cantidad", notificacionService.contarNoLeidas(usuarioActual.id()));
    }

    @POST
    @Path("/{id}/leida")
    public void marcarLeida(@PathParam("id") Long id) {
        notificacionService.marcarLeida(usuarioActual.id(), id);
    }

    @POST
    @Path("/leer-todas")
    public void marcarTodasLeidas() {
        notificacionService.marcarTodasLeidas(usuarioActual.id());
    }
}
