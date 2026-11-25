package ar.com.mediconecta.pagos.business;

import ar.com.mediconecta.pagos.data.PagoRepository;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Cada minuto:
 *  1. concilia los pagos PENDIENTES (cobros con resultado incierto). Asi un pago que la pasarela
 *     aprobo tarde queda registrado, y si el turno se cancelo mientras tanto, se devuelve;
 *  2. busca cobros duplicados de los turnos pagados en las ultimas horas (un pedido viejo que
 *     llego tarde a la pasarela) y los devuelve (D-11).
 * Cada pago y cada turno van en su propia transaccion: uno que falle no frena al resto.
 */
@Singleton
public class ConciliacionPagos {

    private static final Logger LOG = Logger.getLogger(ConciliacionPagos.class.getName());

    /** Horas hacia atras en que se buscan cobros duplicados de turnos pagados. */
    static final long HORAS_POR_DEFECTO = 48;

    @Inject
    private PagoRepository repository;

    @Inject
    private IntentosPago intentos;

    static long horasDuplicados() {
        return Long.getLong("mediconecta.pagos.horasDuplicados", HORAS_POR_DEFECTO);
    }

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
        for (Long turno : repository.turnosPagadosDesde(LocalDateTime.now().minusHours(horasDuplicados()))) {
            try {
                intentos.devolverDuplicados(turno);
            } catch (RuntimeException e) {
                LOG.warning("[Pagos] no se pudieron revisar los cobros del turno #" + turno + ": " + e.getMessage());
            }
        }
    }
}
