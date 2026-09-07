package ar.com.mediconecta.historiaclinica.business;

import ar.com.mediconecta.historiaclinica.data.HistoriaClinicaRepository;
import ar.com.mediconecta.historiaclinica.model.Diagnostico;
import ar.com.mediconecta.historiaclinica.model.HistoriaClinica;
import ar.com.mediconecta.historiaclinica.model.Receta;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.logging.Logger;

@Stateless
public class HistoriaClinicaService implements IHistoriaClinicaService {

    private static final Logger LOG = Logger.getLogger(HistoriaClinicaService.class.getName());

    @Inject
    private HistoriaClinicaRepository repository;

    @PostConstruct
    public void init() {
        LOG.info("[Ciclo de vida] HistoriaClinicaService @Stateless CREADO por WildFly (entra al pool) - instancia #" + System.identityHashCode(this));
    }

    @PreDestroy
    public void destroy() {
        LOG.info("[Ciclo de vida] HistoriaClinicaService @Stateless DESTRUIDO por WildFly (sale del pool) - instancia #" + System.identityHashCode(this));
    }

    @Override
    public HistoriaClinica consultarHistoria(Long pacienteId) {
        return new HistoriaClinica(
                pacienteId,
                repository.buscarDiagnosticosPorPaciente(pacienteId),
                repository.buscarRecetasPorPaciente(pacienteId)
        );
    }

    @Override
    public void registrarDiagnostico(Long pacienteId, Diagnostico diagnostico) {
        diagnostico.setPacienteId(pacienteId);
        diagnostico.setFecha(LocalDateTime.now());
        repository.guardarDiagnostico(diagnostico);
    }

    @Override
    public void registrarReceta(Long pacienteId, Receta receta) {
        receta.setPacienteId(pacienteId);
        receta.setFecha(LocalDateTime.now());
        repository.guardarReceta(receta);
    }
}