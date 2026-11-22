package ar.com.mediconecta.ia.presentation;

import ar.com.mediconecta.config.ApiError;
import ar.com.mediconecta.ia.business.GatewayIA;
import ar.com.mediconecta.ia.business.Orientacion;
import ar.com.mediconecta.ia.business.OrientadorService;
import ar.com.mediconecta.seguridad.UsuarioActual;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * POST /ia/orientacion  {"sintomas": "me pica la piel hace una semana"}
 *
 * Solo pacientes (403 para otros roles). Respuestas de error en Problem Details:
 * 400 texto vacio o largo, 401 sin sesion, 429 demasiadas consultas (con Retry-After).
 * Si la IA falla o tarda mas de 5 s responde 200 con fuente RESPALDO: el paciente
 * siempre recibe una orientacion.
 */
@Path("/ia")
public class OrientacionResource {

    @Inject
    private OrientadorService orientador;

    @Inject
    private UsuarioActual usuarioActual;

    public record ConsultaOrientacion(String sintomas) {}

    @POST
    @Path("/orientacion")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Orientacion orientar(ConsultaOrientacion consulta) {
        // El cuerpo se valida en el borde HTTP: sin texto no se llega al servicio.
        if (consulta == null || consulta.sintomas() == null || consulta.sintomas().isBlank()) {
            throw new BadRequestException(ApiError.respuesta(Response.Status.BAD_REQUEST, GatewayIA.TEXTO_VACIO));
        }
        return orientador.orientar(usuarioActual.id(), consulta.sintomas());
    }
}
