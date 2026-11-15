package ar.com.mediconecta.pagos.business;

import ar.com.mediconecta.pagos.data.PagoRepository;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;
import java.util.logging.Logger;

/**
 * Cada minuto concilia los pagos PENDIENTES (cobros con resultado incierto). Asi un pago que
 * la pasarela aprobo tarde queda registrado, y si el turno se cancelo mientras tanto, se
 * devuelve. Cada pago se concilia en su propia transaccion: uno que falle no frena al resto.
 */
@Singleton
public class ConciliacionPagos {

    private static final Logger LOG = Logger.getLogger(ConciliacionPagos.class.getName());

    @Inject
    private PagoRepository repository;

    @Inject
    private IntentosPago intentos;

    @Schedule(hour = "*", minute = "*", persistent = false, info = "Conciliacion de pagos pendientes")
    public void conciliarPendientes() {
        for (Long id : repository.idsPendientes()) {
            try {
                intentos.conciliar(id);
            } catch (RuntimeException e) {
                // Pasarela caida u otro problema: se reintenta en la proxima corrida.
                LOG.warning("[Pagos] no se pudo conciliar el intento #" + id + ": " + e.getMessage());
            }
        }
    }
}
