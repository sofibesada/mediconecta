package ar.com.mediconecta.pagos.data;

import ar.com.mediconecta.pagos.model.Pago;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.List;

@ApplicationScoped
public class PagoRepository {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public Pago guardar(Pago pago) {
        em.persist(pago);
        return pago;
    }

    /** Todos los intentos de pago de un turno, del mas nuevo al mas viejo. */
    public List<Pago> listarDeTurno(Long turnoId) {
        return em.createQuery("SELECT p FROM Pago p WHERE p.turnoId = :turno ORDER BY p.creadoEn DESC", Pago.class)
                .setParameter("turno", turnoId)
                .getResultList();
    }

    /** Pagos aprobados de varios turnos en una sola consulta (para los listados). */
    public List<Pago> aprobadosDe(Collection<Long> turnoIds) {
        if (turnoIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery("SELECT p FROM Pago p WHERE p.turnoId IN :turnos AND p.estado = :estado", Pago.class)
                .setParameter("turnos", turnoIds)
                .setParameter("estado", Pago.Estado.APROBADO)
                .getResultList();
    }

    public List<Pago> listarRecientes(int maximo) {
        return em.createQuery("SELECT p FROM Pago p ORDER BY p.creadoEn DESC", Pago.class)
                .setMaxResults(maximo)
                .getResultList();
    }
}
