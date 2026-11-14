package ar.com.mediconecta.historiaclinica.business;

import ar.com.mediconecta.historiaclinica.data.CifradoClinicoRepository;
import ar.com.mediconecta.historiaclinica.data.CifradoClinicoRepository.ValorGuardado;
import ar.com.mediconecta.seguridad.CifradoDatos;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.util.List;
import java.util.logging.Logger;

/**
 * Cifra los datos clinicos que se guardaron antes de SEC-10 (todo en una transaccion: o
 * quedan todos cifrados o ninguno). Es idempotente: si no hay nada en texto plano, no hace
 * nada. La llama MigracionCifradoClinico al desplegar.
 */
@Stateless
public class MigracionCifradoService {

    private static final Logger LOG = Logger.getLogger(MigracionCifradoService.class.getName());

    @Inject
    private CifradoClinicoRepository repository;

    private CifradoDatos cifrado;

    /** Para las pruebas: un cifrador con una clave conocida. */
    void usar(CifradoDatos cifrado) {
        this.cifrado = cifrado;
    }

    private CifradoDatos cifrado() {
        return cifrado != null ? cifrado : CifradoDatos.configurado();
    }

    /** Devuelve cuantos valores cifro. */
    @PermitAll
    public int cifrarDatosExistentes() {
        CifradoDatos c = cifrado(); // sin clave falla aca, antes de tocar la base
        repository.ampliarColumnas();
        List<ValorGuardado> pendientes = repository.sinCifrar(CifradoDatos.PREFIJO);
        for (ValorGuardado v : pendientes) {
            repository.actualizar(v, c.cifrar(v.valor()));
        }
        // Solo cantidades: nunca el contenido.
        LOG.info("[Cifrado] datos clinicos en texto plano cifrados al arrancar: " + pendientes.size());
        return pendientes.size();
    }
}
