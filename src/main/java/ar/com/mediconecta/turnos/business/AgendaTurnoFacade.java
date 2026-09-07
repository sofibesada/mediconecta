package ar.com.mediconecta.turnos.business;

import ar.com.mediconecta.usuarios.business.IUsuarioService;
import ar.com.mediconecta.turnos.model.Turno;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class AgendaTurnoFacade {

    private static final Logger LOG = Logger.getLogger(AgendaTurnoFacade.class.getName());

    @Inject
    private IUsuarioService usuarioService;

    @Inject
    private ITurnoService turnoService;

    @PostConstruct
    public void init() {
        LOG.info("[Ciclo de vida] AgendaTurnoFacade @ApplicationScoped CREADO por el contenedor CDI - instancia #" + System.identityHashCode(this));
    }

    @PreDestroy
    public void destroy() {
        LOG.info("[Ciclo de vida] AgendaTurnoFacade @ApplicationScoped DESTRUIDO al parar la aplicacion - instancia #" + System.identityHashCode(this));
    }

    public Turno agendarTurno(Long pacienteId, Long turnoId) {
        if (!usuarioService.existeUsuario(pacienteId)) {
            throw new IllegalArgumentException("El paciente no existe");
        }
        return turnoService.reservarTemporalmente(pacienteId, turnoId);
    }
}