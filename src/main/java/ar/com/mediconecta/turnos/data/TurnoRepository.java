package ar.com.mediconecta.turnos.data;

import ar.com.mediconecta.turnos.model.EstadoTurno;
import ar.com.mediconecta.turnos.model.Turno;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class TurnoRepository {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public Turno guardar(Turno turno) {
        em.persist(turno);
        return turno;
    }

    public Turno buscarPorId(Long id) {
        return em.find(Turno.class, id);
    }

    public List<Turno> listarDisponiblesPorProfesional(Long profesionalId) {
        return em.createQuery(
                        "SELECT t FROM Turno t WHERE t.profesionalId = :profesionalId AND t.estado = :estado",
                        Turno.class)
                .setParameter("profesionalId", profesionalId)
                .setParameter("estado", EstadoTurno.DISPONIBLE)
                .getResultList();
    }

    public List<Turno> listarPorPaciente(Long pacienteId) {
        return em.createQuery(
                        "SELECT t FROM Turno t WHERE t.pacienteId = :pacienteId ORDER BY t.fechaHora",
                        Turno.class)
                .setParameter("pacienteId", pacienteId)
                .getResultList();
    }

    public List<Turno> listarPorProfesional(Long profesionalId) {
        return em.createQuery(
                        "SELECT t FROM Turno t WHERE t.profesionalId = :profesionalId ORDER BY t.fechaHora",
                        Turno.class)
                .setParameter("profesionalId", profesionalId)
                .getResultList();
    }

    public Turno actualizar(Turno turno) {
        return em.merge(turno);
    }
}
