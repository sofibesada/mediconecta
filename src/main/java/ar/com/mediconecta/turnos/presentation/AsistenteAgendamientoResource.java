package ar.com.mediconecta.turnos.presentation;

import ar.com.mediconecta.turnos.business.AsistenteAgendamiento;
import ar.com.mediconecta.turnos.business.EstadoAsistente;
import ar.com.mediconecta.turnos.model.Turno;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import jakarta.ejb.NoSuchEJBException;
import jakarta.ejb.NoSuchObjectLocalException;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * Reserva paso a paso, atendida por el componente STATEFUL AsistenteAgendamiento.
 *
 * Como cada pedido HTTP es independiente, la referencia a la instancia @Stateful del
 * paciente se guarda en su sesion HTTP (cookie JSESSIONID): todos los pedidos de ese
 * navegador hablan con LA MISMA instancia hasta que confirma o abandona (@Remove) o
 * hasta que vence el @StatefulTimeout.
 *
 *   GET    /asistente-agendamiento?pacienteId=   que recuerda el asistente
 *   PUT    /asistente-agendamiento/profesional   paso 1: elegir profesional (devuelve horarios)
 *   POST   /asistente-agendamiento/reserva       paso 2: retener un horario
 *   POST   /asistente-agendamiento/confirmacion  paso 3: confirmar (termina la conversacion)
 *   DELETE /asistente-agendamiento?pacienteId=   abandonar (libera el horario, termina)
 */
@Path("/asistente-agendamiento")
@Produces(MediaType.APPLICATION_JSON)
public class AsistenteAgendamientoResource {

    private static final String ATRIBUTO_SESION = "mediconecta.asistenteAgendamiento";

    @Context
    private HttpServletRequest request;

    @Inject
    private IUsuarioService usuarioService;

    @GET
    public EstadoAsistenteResponse estado(@QueryParam("pacienteId") Long pacienteId) {
        return aResponse(conAsistente(a -> a.estado(pacienteId)), null);
    }

    @PUT
    @Path("/profesional")
    @Consumes(MediaType.APPLICATION_JSON)
    public EstadoAsistenteResponse elegirProfesional(AsistenteRequest req) {
        EstadoAsistente e = conAsistente(a -> a.elegirProfesional(req.pacienteId, req.profesionalId));
        return aResponse(e, e.horarios);
    }

    @POST
    @Path("/reserva")
    @Consumes(MediaType.APPLICATION_JSON)
    public EstadoAsistenteResponse reservar(AsistenteRequest req) {
        return aResponse(conAsistente(a -> a.reservar(req.pacienteId, req.turnoId)), null);
    }

    @POST
    @Path("/confirmacion")
    @Consumes(MediaType.APPLICATION_JSON)
    public TurnoResponse confirmar(AsistenteRequest req) {
        Turno confirmado = conAsistente(a -> a.confirmar(req.pacienteId));
        olvidarAsistente(); // @Remove: la instancia ya no existe
        return turnoResponse(confirmado);
    }

    @DELETE
    public void abandonar(@QueryParam("pacienteId") Long pacienteId) {
        conAsistente(a -> {
            a.abandonar(pacienteId);
            return null;
        });
        olvidarAsistente();
    }

    // ---------- manejo de la instancia @Stateful en la sesion HTTP ----------

    /**
     * Ejecuta la accion sobre el asistente de este navegador. Si la instancia ya no
     * existe (vencio el @StatefulTimeout), se empieza una conversacion nueva.
     */
    private <T> T conAsistente(Function<AsistenteAgendamiento, T> accion) {
        try {
            return accion.apply(asistenteDeLaSesion());
        } catch (NoSuchEJBException | NoSuchObjectLocalException e) {
            olvidarAsistente();
            return accion.apply(asistenteDeLaSesion());
        }
    }

    private AsistenteAgendamiento asistenteDeLaSesion() {
        HttpSession sesion = request.getSession(true);
        AsistenteAgendamiento asistente = (AsistenteAgendamiento) sesion.getAttribute(ATRIBUTO_SESION);
        if (asistente == null) {
            asistente = nuevoAsistente();
            sesion.setAttribute(ATRIBUTO_SESION, asistente);
        }
        return asistente;
    }

    /** Cada lookup de un bean @Stateful devuelve una instancia NUEVA. */
    private AsistenteAgendamiento nuevoAsistente() {
        try {
            return (AsistenteAgendamiento) new InitialContext().lookup("java:module/AsistenteAgendamiento");
        } catch (NamingException e) {
            throw new IllegalStateException("No se pudo crear el asistente de agendamiento", e);
        }
    }

    private void olvidarAsistente() {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.removeAttribute(ATRIBUTO_SESION);
        }
    }

    // ---------- mapeo a DTOs ----------

    private EstadoAsistenteResponse aResponse(EstadoAsistente e, List<Turno> horarios) {
        EstadoAsistenteResponse r = new EstadoAsistenteResponse();
        r.asistenteId = e.asistenteId;
        r.iniciadoEn = e.iniciadoEn;
        r.pedidosAtendidos = e.pedidosAtendidos;
        r.profesionalId = e.profesionalId;
        r.turnoRetenido = e.turnoRetenido != null ? turnoResponse(e.turnoRetenido) : null;
        if (horarios != null) {
            r.horarios = horarios.stream().map(this::turnoResponse).collect(Collectors.toList());
        }
        return r;
    }

    private TurnoResponse turnoResponse(Turno t) {
        return TurnoResponse.from(t, usuarioService.nombreDe(t.getProfesionalId()), usuarioService.nombreDe(t.getPacienteId()));
    }
}
