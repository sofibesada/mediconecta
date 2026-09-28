package ar.com.mediconecta.notificaciones.business;

import ar.com.mediconecta.notificaciones.data.NotificacionRepository;
import ar.com.mediconecta.notificaciones.model.Notificacion;
import ar.com.mediconecta.turnos.eventos.TurnoEvento;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

@Stateless
public class NotificacionService implements INotificacionService {

    private static final Logger LOG = Logger.getLogger(NotificacionService.class.getName());
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int MAX_LISTADO = 50;

    @Inject
    private NotificacionRepository repository;

    @Inject
    private IUsuarioService usuarioService;

    @Override
    public void procesarEventoTurno(TurnoEvento e) {
        String profesional = usuarioService.nombreDe(e.getProfesionalId());
        String paciente = usuarioService.nombreDe(e.getPacienteId());
        String fecha = formatear(e.getFechaHora());

        switch (e.getTipo()) {
            case TURNO_CONFIRMADO -> {
                crear(e, e.getPacienteId(), "Turno confirmado",
                        "Tu turno con " + profesional + " el " + fecha + " quedó confirmado.");
                crear(e, e.getProfesionalId(), "Nuevo turno",
                        paciente + " confirmó un turno para el " + fecha + ".");
            }
            case TURNO_CANCELADO -> {
                crear(e, e.getPacienteId(), "Turno cancelado",
                        "Tu turno con " + profesional + " del " + fecha + " fue cancelado.");
                crear(e, e.getProfesionalId(), "Turno cancelado",
                        "Se canceló el turno de " + paciente + " del " + fecha + ".");
            }
            case TURNO_REPROGRAMADO -> {
                String anterior = formatear(e.getFechaHoraAnterior());
                crear(e, e.getPacienteId(), "Turno reprogramado",
                        "Tu turno con " + profesional + " pasó del " + anterior + " al " + fecha + ".");
                crear(e, e.getProfesionalId(), "Turno reprogramado",
                        "El turno de " + paciente + " pasó del " + anterior + " al " + fecha + ".");
            }
        }
    }

    /**
     * Idempotencia: JMS garantiza entrega "al menos una vez", asi que el mismo
     * evento puede llegar dos veces. Si ya existe la notificacion de ese evento
     * para ese usuario, no se crea otra.
     */
    private void crear(TurnoEvento e, Long usuarioId, String titulo, String mensaje) {
        if (usuarioId == null) {
            return;
        }
        if (repository.existe(e.getEventoId(), usuarioId)) {
            LOG.info("[Notificaciones] evento " + e.getEventoId() + " ya procesado para usuario #" + usuarioId + ", se ignora");
            return;
        }
        Notificacion n = new Notificacion();
        n.setEventoId(e.getEventoId());
        n.setUsuarioId(usuarioId);
        n.setTurnoId(e.getTurnoId());
        n.setTipo(e.getTipo().name());
        n.setTitulo(titulo);
        n.setMensaje(mensaje);
        n.setCreadaEn(LocalDateTime.now());
        repository.guardar(n);

        // Envio simulado: en un sistema real aca se mandaria un mail/SMS/push.
        LOG.info("[EMAIL simulado] Para: usuario #" + usuarioId + " | " + titulo + " | " + mensaje);
    }

    @Override
    public List<Notificacion> listarDeUsuario(Long usuarioId) {
        return repository.listarPorUsuario(usuarioId, MAX_LISTADO);
    }

    @Override
    public long contarNoLeidas(Long usuarioId) {
        return repository.contarNoLeidas(usuarioId);
    }

    @Override
    public void marcarLeida(Long usuarioId, Long notificacionId) {
        Notificacion n = repository.buscarPorId(notificacionId);
        // Un usuario solo puede marcar sus propias notificaciones.
        if (n == null || !n.getUsuarioId().equals(usuarioId)) {
            throw new IllegalArgumentException("Notificación inexistente");
        }
        n.setLeida(true);
    }

    @Override
    public void marcarTodasLeidas(Long usuarioId) {
        repository.marcarTodasLeidas(usuarioId);
    }

    private String formatear(LocalDateTime fecha) {
        return fecha != null ? fecha.format(FORMATO) : "—";
    }
}