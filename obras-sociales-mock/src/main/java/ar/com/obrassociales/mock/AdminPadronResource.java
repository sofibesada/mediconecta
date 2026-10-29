package ar.com.obrassociales.mock;

import ar.com.obrassociales.mock.Padron.Afiliado;
import ar.com.obrassociales.mock.Padron.Entidad;
import ar.com.obrassociales.mock.Padron.Medicamento;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Back office del sistema legado (lo usa el panel admin.html): alta, modificacion y baja
 * de obras sociales, afiliados y medicamentos del vademecum. No es parte del contrato
 * con MediConecta, que solo usa el servicio SOAP.
 *
 * Exige el header X-Admin-Key (system property obrassociales.mock.adminKey o variable
 * de entorno OBRASSOCIALES_ADMIN_KEY; sin clave configurada, el panel queda cerrado).
 */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminPadronResource {

    @ApplicationPath("/api")
    public static class Api extends Application {}

    @GET
    public Response padron(@HeaderParam("X-Admin-Key") String clave) {
        return conClave(clave, Padron::datos);
    }

    @POST
    @Path("/obras-sociales")
    public Response guardarEntidad(@HeaderParam("X-Admin-Key") String clave, Entidad e) {
        return conClave(clave, () -> { Padron.guardarEntidad(e); return e; });
    }

    @DELETE
    @Path("/obras-sociales/{codigo}")
    public Response borrarEntidad(@HeaderParam("X-Admin-Key") String clave, @PathParam("codigo") String codigo) {
        return conClave(clave, () -> { Padron.borrarEntidad(codigo); return Map.of("borrado", codigo); });
    }

    @POST
    @Path("/afiliados")
    public Response guardarAfiliado(@HeaderParam("X-Admin-Key") String clave, Afiliado a) {
        return conClave(clave, () -> { Padron.guardarAfiliado(a); return a; });
    }

    @DELETE
    @Path("/afiliados/{obraSocial}/{numero}")
    public Response borrarAfiliado(@HeaderParam("X-Admin-Key") String clave, @PathParam("obraSocial") String obraSocial,
                                   @PathParam("numero") String numero) {
        return conClave(clave, () -> { Padron.borrarAfiliado(obraSocial, numero); return Map.of("borrado", numero); });
    }

    @POST
    @Path("/medicamentos")
    public Response guardarMedicamento(@HeaderParam("X-Admin-Key") String clave, Medicamento m) {
        return conClave(clave, () -> { Padron.guardarMedicamento(m); return m; });
    }

    @DELETE
    @Path("/medicamentos/{obraSocial}/{nombre}")
    public Response borrarMedicamento(@HeaderParam("X-Admin-Key") String clave, @PathParam("obraSocial") String obraSocial,
                                      @PathParam("nombre") String nombre) {
        return conClave(clave, () -> { Padron.borrarMedicamento(obraSocial, nombre); return Map.of("borrado", nombre); });
    }

    private static Response conClave(String clave, Supplier<Object> accion) {
        // La clave se configura afuera (system property o variable de entorno OBRASSOCIALES_ADMIN_KEY).
        String esperada = System.getProperty("obrassociales.mock.adminKey", System.getenv("OBRASSOCIALES_ADMIN_KEY"));
        if (esperada == null || !esperada.equals(clave)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "Clave de administrador incorrecta")).build();
        }
        try {
            return Response.ok(accion.get()).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
        }
    }
}
