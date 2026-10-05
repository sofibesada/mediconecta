package ar.com.mediconecta.turnos.business;

import ar.com.mediconecta.turnos.model.EstadoTurno;
import ar.com.mediconecta.turnos.model.Turno;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.EJB;
import jakarta.ejb.LocalBean;
import jakarta.ejb.PostActivate;
import jakarta.ejb.PrePassivate;
import jakarta.ejb.Remove;
import jakarta.ejb.Stateful;
import jakarta.ejb.StatefulTimeout;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Componente STATEFUL: lleva la conversacion de un paciente mientras agenda un turno
 * (elegir profesional -> reservar -> confirmar). Cada navegador tiene SU instancia,
 * que recuerda entre pedidos HTTP que profesional eligio y que turno tiene retenido.
 *
 * - La referencia a la instancia se guarda en la sesion HTTP (ver
 *   AsistenteAgendamientoResource), asi el mismo paciente vuelve siempre a la misma.
 * - confirmar() y abandonar() son @Remove: terminan la conversacion y el contenedor
 *   destruye la instancia.
 * - @StatefulTimeout: si el paciente deja de interactuar 5 minutos (lo mismo que dura
 *   la reserva temporal), el contenedor descarta la instancia solo.
 *
 * Las reglas de negocio no estan aca: delega en TurnoService (@Stateless).
 */
@Stateful
@LocalBean
@StatefulTimeout(value = 5, unit = TimeUnit.MINUTES)
public class AsistenteAgendamiento implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(AsistenteAgendamiento.class.getName());

    @EJB
    private ITurnoService turnoService;

    // ---------- estado conversacional (propio de ESTE paciente) ----------
    private Long pacienteId;
    private Long profesionalId;
    private Long turnoRetenidoId;
    private LocalDateTime iniciadoEn;
    private int pedidosAtendidos;

    @PostConstruct
    void crear() {
        iniciadoEn = LocalDateTime.now();
        LOG.info("[Ciclo de vida] AsistenteAgendamiento @Stateful CREADO - instancia #" + id() + " (empieza una conversacion)");
    }

    @PreDestroy
    void destruir() {
        LOG.info("[Ciclo de vida] AsistenteAgendamiento @Stateful DESTRUIDO - instancia #" + id()
                + " despues de " + pedidosAtendidos + " pedidos del paciente #" + pacienteId);
    }

    @PrePassivate
    void pasivar() {
        LOG.info("[Ciclo de vida] AsistenteAgendamiento #" + id() + " PASIVADO (el contenedor lo guarda fuera de memoria)");
    }

    @PostActivate
    void activar() {
        LOG.info("[Ciclo de vida] AsistenteAgendamiento #" + id() + " ACTIVADO de nuevo");
    }

    // ---------- pasos de la conversacion ----------

    public EstadoAsistente estado(Long pacienteId) {
        atender(pacienteId, "consulta su reserva en curso");
        return foto();
    }

    /** Paso 1: el paciente elige un profesional; se recuerda y se devuelven sus horarios. */
    public EstadoAsistente elegirProfesional(Long pacienteId, Long profesionalId) {
        atender(pacienteId, "elige al profesional #" + profesionalId);
        this.profesionalId = profesionalId;
        EstadoAsistente e = foto();
        e.horarios = turnoService.consultarDisponibilidad(profesionalId);
        return e;
    }

    /** Paso 2: retiene un horario. Si ya tenia otro retenido, lo suelta primero. */
    public EstadoAsistente reservar(Long pacienteId, Long turnoId) {
        atender(pacienteId, "reserva el turno #" + turnoId);
        if (turnoRetenidoId != null && !turnoRetenidoId.equals(turnoId)) {
            turnoService.liberarReservaTemporal(pacienteId, turnoRetenidoId);
        }
        Turno turno = turnoService.reservarTemporalmente(pacienteId, turnoId);
        turnoRetenidoId = turno.getId();
        profesionalId = turno.getProfesionalId();
        return foto();
    }

    /**
     * Paso 3: confirma el turno retenido y TERMINA la conversacion (@Remove).
     * retainIfException: si la confirmacion falla (ej. vencio la reserva), la instancia
     * sigue viva para que el paciente pueda elegir otro horario.
     */
    @Remove(retainIfException = true)
    public Turno confirmar(Long pacienteId) {
        atender(pacienteId, "confirma su turno");
        if (turnoRetenidoId == null) {
            throw new ConflictoTurnoException("No tenés ningún turno reservado para confirmar");
        }
        try {
            Turno confirmado = turnoService.confirmarTurno(turnoRetenidoId);
            LOG.info("[Asistente @Stateful #" + id() + "] conversacion terminada: turno #" + confirmado.getId() + " confirmado");
            return confirmado;
        } catch (ConflictoTurnoException e) {
            turnoRetenidoId = null; // la reserva vencio o ya no es valida
            throw e;
        }
    }

    /** El paciente abandona: se libera el horario retenido y TERMINA la conversacion (@Remove). */
    @Remove
    public void abandonar(Long pacienteId) {
        atender(pacienteId, "abandona la reserva");
        soltarTurnoRetenido();
    }

    // ---------- internos ----------

    private void atender(Long pacienteId, String accion) {
        if (pacienteId == null) {
            throw new DatosTurnoInvalidosException("Falta el paciente");
        }
        if (this.pacienteId != null && !this.pacienteId.equals(pacienteId)) {
            // Otro usuario en el mismo navegador: arranca de cero.
            soltarTurnoRetenido();
            profesionalId = null;
        }
        this.pacienteId = pacienteId;
        pedidosAtendidos++;
        LOG.info("[Asistente @Stateful #" + id() + "] pedido " + pedidosAtendidos + " del paciente #" + pacienteId
                + ": " + accion + " (recuerda: profesional=" + profesionalId + ", turno retenido=" + turnoRetenidoId + ")");
    }

    private void soltarTurnoRetenido() {
        if (turnoRetenidoId != null) {
            turnoService.liberarReservaTemporal(this.pacienteId, turnoRetenidoId);
            turnoRetenidoId = null;
        }
    }

    private EstadoAsistente foto() {
        Turno retenido = null;
        if (turnoRetenidoId != null) {
            retenido = turnoService.buscarTurno(turnoRetenidoId);
            boolean sigueSiendoSuyo = retenido != null && retenido.getEstado() == EstadoTurno.RESERVADO_TEMPORAL
                    && pacienteId.equals(retenido.getPacienteId())
                    && retenido.getHoldExpiraEn() != null && retenido.getHoldExpiraEn().isAfter(LocalDateTime.now());
            if (!sigueSiendoSuyo) {
                soltarTurnoRetenido(); // vencio: se libera el horario
                retenido = null;
            }
        }
        EstadoAsistente e = new EstadoAsistente();
        e.asistenteId = id();
        e.iniciadoEn = iniciadoEn;
        e.pedidosAtendidos = pedidosAtendidos;
        e.profesionalId = profesionalId;
        e.turnoRetenido = retenido;
        return e;
    }

    private int id() {
        return System.identityHashCode(this);
    }
}
