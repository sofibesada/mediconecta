package ar.com.mediconecta.telemedicina.data;

import ar.com.mediconecta.telemedicina.model.SalaVirtual;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class SalaVirtualRepository {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public SalaVirtual guardar(SalaVirtual sala) {
        em.persist(sala);
        return sala;
    }

    public SalaVirtual buscarPorTurno(Long turnoId) {
        List<SalaVirtual> r = em.createQuery("SELECT s FROM SalaVirtual s WHERE s.turnoId = :turnoId", SalaVirtual.class)
                .setParameter("turnoId", turnoId)
                .getResultList();
        return r.isEmpty() ? null : r.get(0);
    }
}
