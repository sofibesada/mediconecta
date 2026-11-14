package ar.com.mediconecta.historiaclinica.data;

import ar.com.mediconecta.historiaclinica.model.AccesoHistoriaClinica;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class AccesoHistoriaClinicaRepository {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public AccesoHistoriaClinica guardar(AccesoHistoriaClinica acceso) {
        em.persist(acceso);
        return acceso;
    }

    /** Consultas a la historia de un paciente, de la mas nueva a la mas vieja. */
    public List<AccesoHistoriaClinica> listarDePaciente(Long pacienteId, int maximo) {
        return em.createQuery("SELECT a FROM AccesoHistoriaClinica a WHERE a.pacienteId = :paciente "
                        + "ORDER BY a.fechaHora DESC, a.id DESC", AccesoHistoriaClinica.class)
                .setParameter("paciente", pacienteId)
                .setMaxResults(maximo)
                .getResultList();
    }

    public List<AccesoHistoriaClinica> listarRecientes(int maximo) {
        return em.createQuery("SELECT a FROM AccesoHistoriaClinica a ORDER BY a.fechaHora DESC, a.id DESC",
                        AccesoHistoriaClinica.class)
                .setMaxResults(maximo)
                .getResultList();
    }
}
