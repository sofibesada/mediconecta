package ar.com.mediconecta.historiaclinica.business;

import ar.com.mediconecta.historiaclinica.data.HistoriaClinicaRepository;
import ar.com.mediconecta.historiaclinica.model.Diagnostico;
import ar.com.mediconecta.historiaclinica.model.HistoriaClinica;
import ar.com.mediconecta.historiaclinica.model.Receta;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
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

    @Inject
    private IUsuarioService usuarioService;

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
        exigirPacienteExistente(pacienteId);
        return new HistoriaClinica(
                pacienteId,
                repository.buscarDiagnosticosPorPaciente(pacienteId),
                repository.buscarRecetasPorPaciente(pacienteId)
        );
    }

    @Override
    public void registrarDiagnostico(Long pacienteId, Diagnostico diagnostico) {
        exigirPacienteExistente(pacienteId);
        if (diagnostico.getDescripcion() == null || diagnostico.getDescripcion().isBlank()) {
            throw new IllegalArgumentException("La descripción del diagnóstico es obligatoria");
        }
        diagnostico.setPacienteId(pacienteId);
        diagnostico.setFecha(LocalDateTime.now());
        repository.guardarDiagnostico(diagnostico);
    }

    @Override
    public void registrarReceta(Long pacienteId, Receta receta) {
        exigirPacienteExistente(pacienteId);
        if (receta.getMedicamento() == null || receta.getMedicamento().isBlank()) {
            throw new IllegalArgumentException("El medicamento de la receta es obligatorio");
        }
        receta.setPacienteId(pacienteId);
        receta.setFecha(LocalDateTime.now());
        repository.guardarReceta(receta);
    }

    private void exigirPacienteExistente(Long pacienteId) {
        if (!usuarioService.existeUsuario(pacienteId)) {
            throw new IllegalArgumentException("El paciente indicado no existe o está desactivado");
        }
    }
}