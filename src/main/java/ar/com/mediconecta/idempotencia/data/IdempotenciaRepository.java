package ar.com.mediconecta.idempotencia.data;

import ar.com.mediconecta.idempotencia.model.RegistroIdempotencia;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;

@ApplicationScoped
public class IdempotenciaRepository {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    /** Registro vigente (creado despues de "desde") para esa clave y ese usuario, o null. */
    public RegistroIdempotencia buscar(String clave, Long usuarioId, LocalDateTime desde) {
        return em.createQuery(
                        "SELECT r FROM RegistroIdempotencia r WHERE r.clave = :clave AND r.usuarioId = :usuarioId AND r.creadoEn >= :desde",
                        RegistroIdempotencia.class)
                .setParameter("clave", clave)
                .setParameter("usuarioId", usuarioId)
                .setParameter("desde", desde)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    public void guardar(RegistroIdempotencia registro) {
        em.persist(registro);
    }
}
