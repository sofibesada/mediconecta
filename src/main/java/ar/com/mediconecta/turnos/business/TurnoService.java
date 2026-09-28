package ar.com.mediconecta.turnos.business;

import ar.com.mediconecta.turnos.data.TurnoRepository;
import ar.com.mediconecta.turnos.eventos.TipoEventoTurno;
import ar.com.mediconecta.turnos.eventos.TurnoEventoPublisher;
import ar.com.mediconecta.turnos.model.EstadoTurno;
import ar.com.mediconecta.turnos.model.Turno;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.Stateful;
import jakarta.ejb.StatefulTimeout;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Stateful
@StatefulTimeout(value = 5, unit = TimeUnit.MINUTES)
public class TurnoService implements ITurnoService {

    private static final int MINUTOS_HOLD = 5;
    private static final Logger LOG = Logger.getLogger(TurnoService.class.getName());

    @Inject
    private TurnoRepository turnoRepository;

    @Inject
    private IUsuarioService usuarioService;

    @Inject
    private TurnoEventoPublisher eventoPublisher;

    @PostConstruct
    public void init() {
        LOG.info("[Ciclo de vida] TurnoService @Stateful CREADO por WildFly - instancia #" + System.identityHashCode(this));
    }

    @PreDestroy
    public void destroy() {
        LOG.info("[Ciclo de vida] TurnoService @Stateful DESTRUIDO por WildFly - instancia #" + System.identityHashCode(this));
    }

    @Override
    public List<Turno> consultarDisponibilidad(Long profesionalId) {
        return turnoRepository.listarDisponiblesPorProfesional(profesionalId);
    }

    @Override
    public List<Turno> listarTurnosDePaciente(Long pacienteId) {
        return turnoRepository.listarPorPaciente(pacienteId);
    }

    @Override
    public List<Turno> listarTurnosDeProfesional(Long profesionalId) {
        return turnoRepository.listarPorProfesional(profesionalId);
    }

    @Override
    public Turno reservarTemporalmente(Long pacienteId, Long turnoId) {
        if (!usuarioService.existeUsuario(pacienteId)) {
            throw new DatosTurnoInvalidosException("El paciente indicado no existe o está desactivado");
        }
        Turno turno = turnoRepository.buscarPorId(turnoId);
        if (turno == null || turno.getEstado() != EstadoTurno.DISPONIBLE) {
            throw new ConflictoTurnoException("El turno no está disponible");
        }
        turno.setPacienteId(pacienteId);
        turno.setEstado(EstadoTurno.RESERVADO_TEMPORAL);
        turno.setHoldExpiraEn(LocalDateTime.now().plusMinutes(MINUTOS_HOLD));
        return turnoRepository.actualizar(turno);
    }

    @Override
    public Turno confirmarTurno(Long turnoId) {
        Turno turno = turnoRepository.buscarPorId(turnoId);
        if (turno == null || turno.getEstado() != EstadoTurno.RESERVADO_TEMPORAL) {
            throw new ConflictoTurnoException("El turno no tiene una reserva temporal activa");
        }
        if (turno.getPacienteId() != null && !usuarioService.existeUsuario(turno.getPacienteId())) {
            throw new ConflictoTurnoException("El paciente de este turno ya no está activo");
        }
        if (turno.getHoldExpiraEn().isBefore(LocalDateTime.now())) {
            turno.setEstado(EstadoTurno.DISPONIBLE);
            turno.setPacienteId(null);
            turnoRepository.actualizar(turno);
            throw new ConflictoTurnoException("El hold del turno expiró, volvé a reservarlo");
        }
        turno.setEstado(EstadoTurno.CONFIRMADO);
        Turno confirmado = turnoRepository.actualizar(turno);
        eventoPublisher.publicar(TipoEventoTurno.TURNO_CONFIRMADO, confirmado, null);
        return confirmado;
    }

    @Override
    public void cancelarTurno(Long turnoId) {
        Turno turno = turnoRepository.buscarPorId(turnoId);
        if (turno == null || turno.getEstado() == EstadoTurno.CANCELADO) {
            return;
        }
        PoliticaCancelacion politica = turno.isUrgente()
                ? new PoliticaCancelacionUrgencia()
                : new PoliticaCancelacionEstandar();

        if (!politica.puedeCancelarse(turno)) {
            throw new ConflictoTurnoException("No se puede cancelar: faltan menos de 24hs para el turno");
        }
        turno.setEstado(EstadoTurno.CANCELADO);
        turnoRepository.actualizar(turno);
        if (turno.getPacienteId() != null) {
            eventoPublisher.publicar(TipoEventoTurno.TURNO_CANCELADO, turno, null);
        }
    }

    @Override
    public Turno reprogramarTurno(Long turnoId, LocalDateTime nuevaFecha) {
        Turno turno = turnoRepository.buscarPorId(turnoId);
        if (turno == null) {
            throw new ConflictoTurnoException("Turno no encontrado");
        }
        LocalDateTime fechaAnterior = turno.getFechaHora();
        turno.setFechaHora(nuevaFecha);
        Turno reprogramado = turnoRepository.actualizar(turno);
        if (reprogramado.getPacienteId() != null) {
            eventoPublisher.publicar(TipoEventoTurno.TURNO_REPROGRAMADO, reprogramado, fechaAnterior);
        }
        return reprogramado;
    }

    @Override
    public Turno crearTurnoDisponible(Long profesionalId, LocalDateTime fechaHora) {
        if (!usuarioService.esProfesional(profesionalId)) {
            throw new DatosTurnoInvalidosException("El usuario indicado no existe o no es un profesional");
        }
        Turno turno = new Turno();
        turno.setProfesionalId(profesionalId);
        turno.setFechaHora(fechaHora);
        turno.setEstado(EstadoTurno.DISPONIBLE);
        return turnoRepository.guardar(turno);
    }
}
