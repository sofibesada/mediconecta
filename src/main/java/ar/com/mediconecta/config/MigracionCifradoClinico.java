package ar.com.mediconecta.config;

import ar.com.mediconecta.historiaclinica.business.MigracionCifradoService;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;

/**
 * Al desplegar, cifra los datos clinicos que hayan quedado en texto plano (SEC-10). Si no
 * hay clave configurada, el despliegue falla: no se arranca guardando datos sin cifrar.
 */
@Singleton
@Startup
public class MigracionCifradoClinico {

    @Inject
    private MigracionCifradoService migracion;

    @PostConstruct
    public void init() {
        migracion.cifrarDatosExistentes();
    }
}
