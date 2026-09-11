package ar.com.mediconecta.turnos.business;

import ar.com.mediconecta.usuarios.business.IUsuarioService;
import ar.com.mediconecta.turnos.model.Turno;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class AgendaTurnoFacade {

    private static final Logger LOG = Logger.getLogger(AgendaTurnoFacade.class.getName());

    @Inject
    private IUsuarioService usuarioService;

    // TurnoService es @Stateful; si se inyectara directo aca (Facade es
    // @ApplicationScoped) CDI le asignaria una unica instancia dependent para
    // toda la vida de la aplicacion, y el contenedor EJB solo permite una
    // invocacion a la vez sobre una instancia stateful -- dos reservas
    // concurrentes se serializarian o fallarian. Instance<> pide una
    // instancia nueva en cada llamada y se la destruye despues.
    @Inject
    private Instance<ITurnoService> turnoServiceProvider;

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
        ITurnoService turnoService = turnoServiceProvider.get();
        try {
            return turnoService.reservarTemporalmente(pacienteId, turnoId);
        } finally {
            turnoServiceProvider.destroy(turnoService);
        }
    }
}