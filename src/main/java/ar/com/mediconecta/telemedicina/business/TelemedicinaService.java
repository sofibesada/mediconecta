package ar.com.mediconecta.telemedicina.business;

import ar.com.mediconecta.telemedicina.data.SalaVirtualRepository;
import ar.com.mediconecta.telemedicina.integracion.SalaExterna;
import ar.com.mediconecta.telemedicina.integracion.VideollamadasClient;
import ar.com.mediconecta.telemedicina.model.SalaVirtual;
import ar.com.mediconecta.turnos.eventos.TurnoEvento;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.logging.Logger;

@Stateless
public class TelemedicinaService implements ITelemedicinaService {

    private static final Logger LOG = Logger.getLogger(TelemedicinaService.class.getName());

    @Inject
    private SalaVirtualRepository repository;

    @Inject
    private VideollamadasClient videollamadas;

    @Override
    public void procesarEventoTurno(TurnoEvento e) {
        switch (e.getTipo()) {
            case TURNO_CONFIRMADO -> crearSalaSiCorresponde(e);
            case TURNO_CANCELADO -> eliminarSalaSiExiste(e);
            // Reprogramado: la sala sigue siendo la misma (el link no depende de la fecha).
            default -> { }
        }
    }

    private void crearSalaSiCorresponde(TurnoEvento e) {
        if (!e.esVirtual()) {
            return;
        }
        SalaVirtual existente = repository.buscarPorTurno(e.getTurnoId());
        if (existente != null && existente.getEstado() == SalaVirtual.Estado.ACTIVA) {
            LOG.info("[Telemedicina] el turno #" + e.getTurnoId() + " ya tiene sala, se ignora el evento repetido");
            return;
        }
        // Llamada SINCRONICA al proveedor externo. Si falla, la excepcion hace rollback
        // y JMS reintenta; el Idempotency-Key evita crear dos salas si el reintento
        // llega despues de que el proveedor ya la habia creado.
        SalaExterna externa = videollamadas.crearSala(e.getTurnoId(), e.getFechaHora(), e.getEventoId());

        SalaVirtual sala = existente != null ? existente : new SalaVirtual();
        sala.setTurnoId(e.getTurnoId());
        sala.setPacienteId(e.getPacienteId());
        sala.setProfesionalId(e.getProfesionalId());
        sala.setSalaExternaId(externa.id);
        sala.setUrl(externa.joinUrl);
        sala.setUrlAnfitrion(externa.hostUrl);
        sala.setEstado(SalaVirtual.Estado.ACTIVA);
        sala.setCreadaEn(LocalDateTime.now());
        if (existente == null) {
            repository.guardar(sala);
        }
        LOG.info("[Telemedicina] sala creada para el turno #" + e.getTurnoId() + ": " + externa.joinUrl);
    }

    private void eliminarSalaSiExiste(TurnoEvento e) {
        SalaVirtual sala = repository.buscarPorTurno(e.getTurnoId());
        if (sala == null || sala.getEstado() == SalaVirtual.Estado.ELIMINADA) {
            return;
        }
        videollamadas.eliminarSala(sala.getSalaExternaId());
        sala.setEstado(SalaVirtual.Estado.ELIMINADA);
        LOG.info("[Telemedicina] sala del turno #" + e.getTurnoId() + " eliminada");
    }

    @Override
    public SalaVirtual obtenerSala(Long turnoId, Long usuarioId) {
        SalaVirtual sala = repository.buscarPorTurno(turnoId);
        if (sala == null || sala.getEstado() != SalaVirtual.Estado.ACTIVA || !sala.participa(usuarioId)) {
            // Mismo mensaje si no existe o si es de otro: no revela turnos ajenos.
            throw new SalaNoDisponibleException("Este turno no tiene una sala virtual disponible (todavía)");
        }
        return sala;
    }
}
