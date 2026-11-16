package ar.com.mediconecta.config;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.logging.Logger;

/**
 * Hibernate crea una restriccion CHECK con los valores del enum Pago.Estado cuando crea la
 * tabla, pero hbm2ddl=update NO la actualiza si despues se agregan estados. En una base creada
 * con una version anterior, guardar un pago PENDIENTE, REEMBOLSADO o FALLIDO fallaria. Al
 * desplegar se vuelve a crear la restriccion con todos los estados (es idempotente).
 */
@Singleton
@Startup
public class ActualizacionEsquemaPagos {

    private static final Logger LOG = Logger.getLogger(ActualizacionEsquemaPagos.class.getName());

    /** Todos los valores de Pago.Estado (EsquemaPagosTest verifica que no falte ninguno). */
    static final String ESTADOS = "'PENDIENTE', 'APROBADO', 'RECHAZADO', 'REEMBOLSADO', 'FALLIDO'";

    static final String SIN_RESTRICCION = "ALTER TABLE pago DROP CONSTRAINT IF EXISTS pago_estado_check";
    static final String CON_RESTRICCION = "ALTER TABLE pago ADD CONSTRAINT pago_estado_check CHECK (estado IN ("
            + "'PENDIENTE', 'APROBADO', 'RECHAZADO', 'REEMBOLSADO', 'FALLIDO'))";

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @PostConstruct
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void actualizar() {
        em.createNativeQuery(SIN_RESTRICCION).executeUpdate();
        em.createNativeQuery(CON_RESTRICCION).executeUpdate();
        LOG.info("[Esquema] restriccion de estados de pago actualizada");
    }
}
