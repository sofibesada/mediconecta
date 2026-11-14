package ar.com.mediconecta.turnos.eventos;

public enum TipoEventoTurno {
    TURNO_CONFIRMADO,
    TURNO_CANCELADO,
    TURNO_REPROGRAMADO,
    /** Lo publica RecordatorioTurnos (@Schedule), no un usuario. */
    TURNO_RECORDATORIO,
    /** Lo publica el componente de pagos cuando la pasarela aprueba el cobro. */
    PAGO_ACREDITADO
}