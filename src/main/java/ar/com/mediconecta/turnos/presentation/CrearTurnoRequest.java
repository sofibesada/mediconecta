package ar.com.mediconecta.turnos.presentation;

import ar.com.mediconecta.turnos.model.Modalidad;
import java.time.LocalDateTime;

public class CrearTurnoRequest {
    public Long profesionalId;
    public LocalDateTime fechaHora;
    /** Opcional: si no viene, el turno es PRESENCIAL. */
    public Modalidad modalidad;
}
