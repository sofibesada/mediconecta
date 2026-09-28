package ar.com.mediconecta.notificaciones.data;

import ar.com.mediconecta.notificaciones.model.Notificacion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class NotificacionRepository {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public Notificacion guardar(Notificacion notificacion) {
        em.persist(notificacion);
        return notificacion;
    }

    public Notificacion buscarPorId(Long id) {
        return em.find(Notificacion.class, id);
    }

    public boolean existe(String eventoId, Long usuarioId) {
        return em.createQuery(
                        "SELECT COUNT(n) FROM Notificacion n WHERE n.eventoId = :eventoId AND n.usuarioId = :usuarioId",
                        Long.class)
                .setParameter("eventoId", eventoId)
                .setParameter("usuarioId", usuarioId)
                .getSingleResult() > 0;
    }

    public List<Notificacion> listarPorUsuario(Long usuarioId, int limite) {
        return em.createQuery(
                        "SELECT n FROM Notificacion n WHERE n.usuarioId = :usuarioId ORDER BY n.creadaEn DESC",
                        Notificacion.class)
                .setParameter("usuarioId", usuarioId)
                .setMaxResults(limite)
                .getResultList();
    }

    public long contarNoLeidas(Long usuarioId) {
        return em.createQuery(
                        "SELECT COUNT(n) FROM Notificacion n WHERE n.usuarioId = :usuarioId AND n.leida = false",
                        Long.class)
                .setParameter("usuarioId", usuarioId)
                .getSingleResult();
    }

    public int marcarTodasLeidas(Long usuarioId) {
        return em.createQuery(
                        "UPDATE Notificacion n SET n.leida = true WHERE n.usuarioId = :usuarioId AND n.leida = false")
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
    }
}