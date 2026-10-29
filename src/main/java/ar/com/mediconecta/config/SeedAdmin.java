package ar.com.mediconecta.config;

import ar.com.mediconecta.usuarios.business.IUsuarioService;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import java.util.logging.Logger;

/**
 * Crea la unica cuenta ADMIN al desplegar, si todavia no existe.
 *
 * La contrasena NO esta en el codigo: se toma de mediconecta.admin.password (system
 * property) o MEDICONECTA_ADMIN_PASSWORD (variable de entorno). Si no hay ninguna, no se
 * siembra nada y se avisa en el log.
 */
@Singleton
@Startup
public class SeedAdmin {

    private static final Logger LOG = Logger.getLogger(SeedAdmin.class.getName());
    private static final String ADMIN_NOMBRE = "Administrador MediConecta";

    @Inject
    private IUsuarioService usuarioService;

    @PostConstruct
    public void init() {
        String email = Config.valor("mediconecta.admin.email", "MEDICONECTA_ADMIN_EMAIL");
        String password = Config.valor("mediconecta.admin.password", "MEDICONECTA_ADMIN_PASSWORD");
        if (password == null) {
            LOG.warning("[Seed] No se configuro MEDICONECTA_ADMIN_PASSWORD: no se crea la cuenta ADMIN");
            return;
        }
        usuarioService.sembrarAdmin(ADMIN_NOMBRE, email != null ? email : "admin@mediconecta.com", password);
    }
}
