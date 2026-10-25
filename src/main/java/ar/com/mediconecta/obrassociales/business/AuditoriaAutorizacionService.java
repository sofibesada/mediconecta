package ar.com.mediconecta.obrassociales.business;

import ar.com.mediconecta.obrassociales.model.IntentoAutorizacion;
import ar.com.mediconecta.obrassociales.model.IntentoAutorizacion.Resultado;
import ar.com.mediconecta.seguridad.Roles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Auditoria de los pedidos de autorizacion a obras sociales.
 *
 * registrar() es REQUIRES_NEW: el contenedor suspende la transaccion de quien lo llama
 * (la confirmacion del turno), abre una nueva, guarda el intento y la confirma. Si
 * despues la confirmacion del turno hace rollback (por ejemplo, porque la obra social
 * rechazo), el intento queda registrado igual.
 */
@Stateless
@PermitAll
public class AuditoriaAutorizacionService {

    private static final Logger LOG = Logger.getLogger(AuditoriaAutorizacionService.class.getName());

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void registrar(Long turnoId, Long pacienteId, String obraSocial, Resultado resultado,
                          String codigoAutorizacion, String motivo) {
        IntentoAutorizacion i = new IntentoAutorizacion();
        i.setTurnoId(turnoId);
        i.setPacienteId(pacienteId);
        i.setObraSocial(obraSocial);
        i.setResultado(resultado);
        i.setCodigoAutorizacion(codigoAutorizacion);
        i.setMotivo(motivo);
        i.setFecha(LocalDateTime.now());
        em.persist(i);
        LOG.info("[Auditoria REQUIRES_NEW] autorizacion turno #" + turnoId + " " + obraSocial + " -> " + resultado);
    }

    @RolesAllowed(Roles.ADMIN)
    public List<IntentoAutorizacion> listarRecientes() {
        return em.createQuery("SELECT i FROM IntentoAutorizacion i ORDER BY i.fecha DESC", IntentoAutorizacion.class)
                .setMaxResults(50)
                .getResultList();
    }
}
