package ar.com.mediconecta.notificaciones.messaging;

import ar.com.mediconecta.turnos.eventos.TurnoEvento;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.util.logging.Logger;
import ar.com.mediconecta.notificaciones.business.INotificacionService;
import jakarta.inject.Inject;

/**
 * Consumidor asincronico de los eventos de turnos. Nadie lo llama: WildFly
 * lo ejecuta en un hilo propio cada vez que llega un mensaje al topic.
 *
 * Suscripcion durable: si la app esta caida o redesplegandose cuando se
 * publica un evento, Artemis lo guarda y lo entrega al volver.
 * maxSession=1: una suscripcion durable se consume de a un mensaje por vez,
 * lo que ademas mantiene el orden de los eventos.
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/topic/TurnosEventos"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "mediconecta-notificaciones"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "notificaciones"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
})
public class NotificacionTurnoMDB implements MessageListener {

    private static final Logger LOG = Logger.getLogger(NotificacionTurnoMDB.class.getName());
    private static final Jsonb JSONB = JsonbBuilder.create();
    @Inject
    private INotificacionService notificacionService;

    @Override
    public void onMessage(Message message) {
        try {
            String json = ((TextMessage) message).getText();
            TurnoEvento evento = JSONB.fromJson(json, TurnoEvento.class);

            LOG.info("[JMS consumidor] hilo=" + Thread.currentThread().getName()
                    + " recibio " + evento.getTipo() + " turno #" + evento.getTurnoId()
                    + " evento=" + evento.getEventoId()
                    + " intento=" + message.getIntProperty("JMSXDeliveryCount"));

            notificacionService.procesarEventoTurno(evento);

        } catch (JMSException e) {
            // Excepcion de sistema: el contenedor hace rollback de la transaccion
            // del MDB y Artemis reentrega el mensaje (hasta 3 veces, despues a la DLQ).
            throw new RuntimeException("No se pudo leer el mensaje JMS", e);
        }
    }
}