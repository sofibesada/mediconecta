package ar.com.mediconecta.telemedicina.messaging;

import ar.com.mediconecta.telemedicina.business.ITelemedicinaService;
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
 * SEGUNDO suscriptor de TurnosEventosTopic (el primero es NotificacionTurnoMDB): cada
 * evento le llega a los dos por separado, sin que TurnoService sepa que existen. Esto
 * es publish/subscribe.
 *
 * El message selector filtra en el broker: este MDB solo recibe los eventos que le
 * interesan (confirmado y cancelado), usando la propiedad "tipo" que pone el productor.
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/topic/TurnosEventos"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "mediconecta-telemedicina"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "telemedicina"),
        @ActivationConfigProperty(propertyName = "messageSelector", propertyValue = "tipo IN ('TURNO_CONFIRMADO', 'TURNO_CANCELADO')"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
})
public class TelemedicinaMDB implements MessageListener {

    private static final Logger LOG = Logger.getLogger(TelemedicinaMDB.class.getName());
    private static final Jsonb JSONB = JsonbBuilder.create();

    @Inject
    private ITelemedicinaService telemedicinaService;

    @Override
    public void onMessage(Message message) {
        try {
            TurnoEvento evento = JSONB.fromJson(((TextMessage) message).getText(), TurnoEvento.class);
            LOG.info("[JMS consumidor telemedicina] hilo=" + Thread.currentThread().getName()
                    + " recibio " + evento.getTipo() + " turno #" + evento.getTurnoId()
                    + " (" + evento.getModalidad() + ") intento=" + message.getIntProperty("JMSXDeliveryCount"));
            telemedicinaService.procesarEventoTurno(evento);
        } catch (JMSException e) {
            throw new RuntimeException("No se pudo leer el mensaje JMS", e);
        }
    }
}
