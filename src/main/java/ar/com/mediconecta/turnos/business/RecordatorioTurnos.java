package ar.com.mediconecta.turnos.business;

import ar.com.mediconecta.turnos.data.TurnoRepository;
import ar.com.mediconecta.turnos.eventos.TipoEventoTurno;
import ar.com.mediconecta.turnos.eventos.TurnoEventoPublisher;
import ar.com.mediconecta.turnos.model.Turno;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Recordatorios automaticos de turnos con el TimerService del contenedor (@Schedule).
 *
 * Nadie llama a este bean: WildFly ejecuta programarRecordatorios() cada minuto. Busca los
 * turnos CONFIRMADOS que empiezan en las proximas 24 horas y todavia no tienen
 * recordatorio, y por cada uno publica TURNO_RECORDATORIO en el mismo topico JMS que el
 * resto de los eventos. El aviso lo genera NotificacionTurnoMDB, igual que los demas; el
 * selector de TelemedicinaMDB lo deja afuera.
 *
 * - @Singleton: una sola instancia, y el contenedor no ejecuta dos corridas a la vez.
 * - persistent = false: si el servidor estuvo apagado, no se "recuperan" las corridas
 *   perdidas; no hace falta, porque la siguiente corrida busca por ventana de tiempo.
 * - La marca recordatorioEnviadoPara y la publicacion van en la misma transaccion: si
 *   falla el envio, el turno no queda marcado y se reintenta en la proxima corrida.
 *
 * Anticipacion configurable con la system property mediconecta.recordatorio.horas.
 */
@Singleton
public class RecordatorioTurnos {

    private static final Logger LOG = Logger.getLogger(RecordatorioTurnos.class.getName());
    private static final long HORAS_POR_DEFECTO = 24;

    @Inject
    private TurnoRepository turnoRepository;

    @Inject
    private TurnoEventoPublisher eventoPublisher;

    @Schedule(hour = "*", minute = "*", persistent = false, info = "Recordatorios de turnos")
    public void programarRecordatorios() {
        LocalDateTime ahora = LocalDateTime.now();
        List<Turno> turnos = turnoRepository.listarParaRecordatorio(ahora, ahora.plusHours(horasDeAnticipacion()));
        for (Turno turno : turnos) {
            turno.setRecordatorioEnviadoPara(turno.getFechaHora());
            turnoRepository.actualizar(turno);
            eventoPublisher.publicar(TipoEventoTurno.TURNO_RECORDATORIO, turno, null);
        }
        if (!turnos.isEmpty()) {
            LOG.info("[Timer @Schedule] hilo=" + Thread.currentThread().getName()
                    + " envio " + turnos.size() + " recordatorio(s) de turno");
        }
    }

    private long horasDeAnticipacion() {
        try {
            return Long.parseLong(System.getProperty("mediconecta.recordatorio.horas", String.valueOf(HORAS_POR_DEFECTO)));
        } catch (NumberFormatException e) {
            return HORAS_POR_DEFECTO;
        }
    }
}
