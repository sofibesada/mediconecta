package ar.com.mediconecta.notificaciones.presentation;

import ar.com.mediconecta.notificaciones.business.INotificacionService;
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

    @GET
    public List<NotificacionResponse> listar(@QueryParam("usuarioId") Long usuarioId) {
        return notificacionService.listarDeUsuario(usuarioId).stream()
                .map(NotificacionResponse::from)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/no-leidas")
    public Map<String, Long> contarNoLeidas(@QueryParam("usuarioId") Long usuarioId) {
        return Map.of("cantidad", notificacionService.contarNoLeidas(usuarioId));
    }

    @POST
    @Path("/{id}/leida")
    public void marcarLeida(@PathParam("id") Long id, @QueryParam("usuarioId") Long usuarioId) {
        notificacionService.marcarLeida(usuarioId, id);
    }

    @POST
    @Path("/leer-todas")
    public void marcarTodasLeidas(@QueryParam("usuarioId") Long usuarioId) {
        notificacionService.marcarTodasLeidas(usuarioId);
    }
}
