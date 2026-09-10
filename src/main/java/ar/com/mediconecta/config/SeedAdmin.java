package ar.com.mediconecta.config;

import ar.com.mediconecta.usuarios.business.IUsuarioService;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;

/**
 * Siembra la cuenta ADMIN al desplegar la aplicacion, si todavia no existe.
 * Es la unica forma de tener un ADMIN: no se puede crear por el registro.
 *
 * Las credenciales viven aca, en el backend. Cambiarlas aca si hace falta.
 */
@Singleton
@Startup
public class SeedAdmin {

    private static final String ADMIN_NOMBRE = "Administrador MediConecta";
    private static final String ADMIN_EMAIL = "admin@mediconecta.com";
    private static final String ADMIN_PASSWORD = "MediConecta.Admin.2026";

    @Inject
    private IUsuarioService usuarioService;

    @PostConstruct
    public void init() {
        usuarioService.sembrarAdmin(ADMIN_NOMBRE, ADMIN_EMAIL, ADMIN_PASSWORD);
    }
}
