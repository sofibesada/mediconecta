package ar.com.mediconecta.facturacion.messaging;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import java.util.logging.Logger;

/**
 * Envia a la COLA de facturacion (punto a punto) el reclamo que hay que procesar.
 *
 * MANDATORY: solo se puede llamar dentro de una transaccion (la de la confirmacion del
 * turno). El envio forma parte de esa transaccion XA: si la confirmacion hace rollback,
 * el mensaje nunca llega a la cola.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.MANDATORY)
public class FacturacionColaPublisher {

    private static final Logger LOG = Logger.getLogger(FacturacionColaPublisher.class.getName());

    @Inject
    private JMSContext jmsContext;

    @Resource(lookup = "java:/jms/queue/Facturacion")
    private Queue cola;

    public void enviar(Long reclamoId, Long turnoId) {
        jmsContext.createProducer().send(cola, "{\"reclamoId\":" + reclamoId + ",\"turnoId\":" + turnoId + "}");
        LOG.info("[JMS cola] hilo=" + Thread.currentThread().getName()
                + " envio a FacturacionQueue el reclamo #" + reclamoId + " (turno #" + turnoId + ")");
    }
}
