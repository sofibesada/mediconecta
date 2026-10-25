package ar.com.mediconecta.facturacion.data;

import ar.com.mediconecta.facturacion.model.Reclamo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class ReclamoRepository {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public Reclamo guardar(Reclamo reclamo) {
        em.persist(reclamo);
        return reclamo;
    }

    public Reclamo buscarPorId(Long id) {
        return em.find(Reclamo.class, id);
    }

    public List<Reclamo> listarRecientes(int maximo) {
        return em.createQuery("SELECT r FROM Reclamo r ORDER BY r.creadoEn DESC", Reclamo.class)
                .setMaxResults(maximo)
                .getResultList();
    }
}
