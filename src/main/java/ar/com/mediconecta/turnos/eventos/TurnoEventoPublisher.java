package ar.com.mediconecta.turnos.eventos;

import ar.com.mediconecta.turnos.model.Turno;
import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.Topic;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;


@Stateless
@TransactionAttribute(TransactionAttributeType.MANDATORY)
public class TurnoEventoPublisher {

    private static final Logger LOG = Logger.getLogger(TurnoEventoPublisher.class.getName());
    private static final Jsonb JSONB = JsonbBuilder.create();

    @Inject
    private JMSContext jmsContext;

    @Resource(lookup = "java:/jms/topic/TurnosEventos")
    private Topic topic;

    public void publicar(TipoEventoTurno tipo, Turno turno, LocalDateTime fechaHoraAnterior) {
        TurnoEvento evento = new TurnoEvento();
        evento.setEventoId(UUID.randomUUID().toString());
        evento.setTipo(tipo);
        evento.setTurnoId(turno.getId());
        evento.setPacienteId(turno.getPacienteId());
        evento.setProfesionalId(turno.getProfesionalId());
        evento.setFechaHora(turno.getFechaHora());
        evento.setFechaHoraAnterior(fechaHoraAnterior);
        evento.setModalidad(turno.getModalidad().name());
        evento.setOcurridoEn(LocalDateTime.now());


        jmsContext.createProducer()
                .setProperty("tipo", tipo.name())
                .send(topic, JSONB.toJson(evento));

        LOG.info("[JMS productor] hilo=" + Thread.currentThread().getName()
                + " publico " + tipo + " turno #" + turno.getId()
                + " evento=" + evento.getEventoId());
    }
}