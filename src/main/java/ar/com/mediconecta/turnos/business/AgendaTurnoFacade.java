package ar.com.mediconecta.turnos.business;

import ar.com.mediconecta.usuarios.business.IUsuarioService;
import ar.com.mediconecta.usuarios.model.Usuario;
import ar.com.mediconecta.turnos.model.Turno;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AgendaTurnoFacade {

    @Inject
    private IUsuarioService usuarioService;

    @Inject
    private ITurnoService turnoService;

    public Turno agendarTurno(Long pacienteId, Long turnoId) {
        Usuario paciente = usuarioService.buscarUsuario(pacienteId);
        if (paciente == null) {
            throw new IllegalArgumentException("El paciente no existe");
        }
        return turnoService.reservarTemporalmente(pacienteId, turnoId);
    }
}