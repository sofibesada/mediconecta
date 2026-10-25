package ar.com.mediconecta.facturacion.messaging;

import ar.com.mediconecta.facturacion.business.IFacturacionService;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Consumidor de la COLA de facturacion (punto a punto). A diferencia de un topico, cada
 * mensaje lo recibe un solo consumidor: el reclamo se procesa una sola vez.
 *
 * Si el sistema legado no responde, la excepcion sale del MDB, el contenedor hace
 * rollback y Artemis reentrega el mensaje (3 intentos, 2 s entre cada uno, despues DLQ).
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/queue/Facturacion"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
})
public class FacturacionMDB implements MessageListener {

    private static final Logger LOG = Logger.getLogger(FacturacionMDB.class.getName());
    private static final Jsonb JSONB = JsonbBuilder.create();

    @Inject
    private IFacturacionService facturacionService;

    @Override
    public void onMessage(Message message) {
        try {
            Map<?, ?> cuerpo = JSONB.fromJson(((TextMessage) message).getText(), Map.class);
            Long reclamoId = ((Number) cuerpo.get("reclamoId")).longValue();
            LOG.info("[JMS cola consumidor] hilo=" + Thread.currentThread().getName() + " recibio reclamo #" + reclamoId
                    + " intento=" + message.getIntProperty("JMSXDeliveryCount"));
            facturacionService.procesarReclamo(reclamoId);
        } catch (JMSException e) {
            throw new RuntimeException("No se pudo leer el mensaje JMS", e);
        }
    }
}
