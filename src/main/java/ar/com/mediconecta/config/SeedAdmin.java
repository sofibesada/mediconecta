package ar.com.mediconecta.config;

import ar.com.mediconecta.usuarios.business.IUsuarioService;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;

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
