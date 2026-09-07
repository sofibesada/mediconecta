package ar.com.mediconecta.historiaclinica.data;

import ar.com.mediconecta.historiaclinica.model.Diagnostico;
import ar.com.mediconecta.historiaclinica.model.Receta;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class HistoriaClinicaRepository {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public List<Diagnostico> buscarDiagnosticosPorPaciente(Long pacienteId) {
        return em.createQuery(
                        "SELECT d FROM Diagnostico d WHERE d.pacienteId = :pacienteId ORDER BY d.fecha DESC",
                        Diagnostico.class)
                .setParameter("pacienteId", pacienteId)
                .getResultList();
    }

    public List<Receta> buscarRecetasPorPaciente(Long pacienteId) {
        return em.createQuery(
                        "SELECT r FROM Receta r WHERE r.pacienteId = :pacienteId ORDER BY r.fecha DESC",
                        Receta.class)
                .setParameter("pacienteId", pacienteId)
                .getResultList();
    }

    public Diagnostico guardarDiagnostico(Diagnostico diagnostico) {
        em.persist(diagnostico);
        return diagnostico;
    }

    public Receta guardarReceta(Receta receta) {
        em.persist(receta);
        return receta;
    }
}