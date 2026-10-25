package ar.com.mediconecta.obrassociales.presentation;

import ar.com.mediconecta.obrassociales.business.IObraSocialService;
import ar.com.mediconecta.obrassociales.business.IObraSocialService.CoberturaVerificada;
import ar.com.mediconecta.obrassociales.business.IObraSocialService.ObraSocialDisponible;
import ar.com.mediconecta.seguridad.UsuarioActual;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import ar.com.mediconecta.usuarios.presentation.UsuarioResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * Cobertura medica del paciente (obra social o prepaga).
 *
 *   GET    /cobertura/obras-sociales  obras sociales con las que opera el sistema legado
 *   PUT    /cobertura                 verifica la cobertura por SOAP y, si esta vigente, la guarda
 *   DELETE /cobertura                 vuelve a atenderse como particular
 *
 * La verificacion es SINCRONICA: el paciente necesita saber en el momento si esta cubierto.
 */
@Path("/cobertura")
@Produces(MediaType.APPLICATION_JSON)
public class CoberturaResource {

    public static class CoberturaRequest {
        public String obraSocial;
        public String numeroAfiliado;
    }

    @Inject
    private IObraSocialService obraSocialService;

    @Inject
    private IUsuarioService usuarioService;

    @Inject
    private UsuarioActual usuarioActual;

    @GET
    @Path("/obras-sociales")
    public List<ObraSocialDisponible> obrasSociales() {
        return obraSocialService.listarObrasSociales();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public UsuarioResponse guardar(CoberturaRequest req) {
        if (req == null || req.obraSocial == null || req.obraSocial.isBlank()
                || req.numeroAfiliado == null || req.numeroAfiliado.isBlank()) {
            throw new IllegalArgumentException("Elegí la obra social y completá el número de afiliado");
        }
        String afiliado = req.numeroAfiliado.trim();
        CoberturaVerificada c = obraSocialService.verificarCobertura(req.obraSocial, afiliado);
        if (!c.cubierta()) {
            throw new IllegalArgumentException("La obra social informó: " + c.motivo());
        }
        return UsuarioResponse.from(usuarioService.guardarCobertura(
                usuarioActual.id(), req.obraSocial, afiliado, c.plan(), c.copagoPorcentaje()));
    }

    @DELETE
    public UsuarioResponse quitar() {
        return UsuarioResponse.from(usuarioService.quitarCobertura(usuarioActual.id()));
    }
}
