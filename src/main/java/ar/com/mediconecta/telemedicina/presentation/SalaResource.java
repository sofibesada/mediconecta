package ar.com.mediconecta.telemedicina.presentation;

import ar.com.mediconecta.seguridad.UsuarioActual;
import ar.com.mediconecta.telemedicina.business.ITelemedicinaService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * La sala es un sub-recurso del turno: GET /api/turnos/{turnoId}/sala. Solo la ven el
 * paciente y el profesional del turno (el usuario sale del token).
 */
@Path("/turnos/{turnoId}/sala")
public class SalaResource {

    @Inject
    private ITelemedicinaService telemedicinaService;

    @Inject
    private UsuarioActual usuarioActual;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public SalaResponse obtener(@PathParam("turnoId") Long turnoId) {
        Long usuarioId = usuarioActual.id();
        return SalaResponse.from(telemedicinaService.obtenerSala(turnoId, usuarioId), usuarioId);
    }
}
