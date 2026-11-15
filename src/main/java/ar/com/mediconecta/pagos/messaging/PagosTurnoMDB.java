package ar.com.mediconecta.pagos.messaging;

import ar.com.mediconecta.pagos.business.PagoService;
import ar.com.mediconecta.turnos.eventos.TurnoEvento;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.util.logging.Logger;

/**
 * Tercer suscriptor del topico de turnos: cuando se cancela un turno, Pagos devuelve lo que
 * se haya pagado. Turnos no conoce a Pagos (publish/subscribe): solo publica el evento.
 *
 * Solo recibe TURNO_CANCELADO (message selector). Si la pasarela no responde, la excepcion
 * revierte la transaccion y Artemis reentrega el mensaje (3 intentos y despues la DLQ).
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/topic/TurnosEventos"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "mediconecta-pagos"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "pagos"),
        @ActivationConfigProperty(propertyName = "messageSelector", propertyValue = "tipo = 'TURNO_CANCELADO'"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
})
public class PagosTurnoMDB implements MessageListener {

    private static final Logger LOG = Logger.getLogger(PagosTurnoMDB.class.getName());
    private static final Jsonb JSONB = JsonbBuilder.create();

    @Inject
    private PagoService pagoService;

    @Override
    public void onMessage(Message message) {
        try {
            TurnoEvento evento = JSONB.fromJson(((TextMessage) message).getText(), TurnoEvento.class);
            LOG.info("[JMS consumidor pagos] turno #" + evento.getTurnoId() + " cancelado: se revisan sus pagos");
            pagoService.reembolsarDeTurno(evento.getTurnoId());
        } catch (JMSException e) {
            throw new RuntimeException("No se pudo leer el mensaje JMS", e);
        }
    }
}
