package ar.com.mediconecta.historiaclinica.business;

import ar.com.mediconecta.historiaclinica.data.HistoriaClinicaRepository;
import ar.com.mediconecta.historiaclinica.model.Diagnostico;
import ar.com.mediconecta.historiaclinica.model.HistoriaClinica;
import ar.com.mediconecta.historiaclinica.model.Receta;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.time.LocalDateTime;

@Stateless
public class HistoriaClinicaService implements IHistoriaClinicaService {

    @Inject
    private HistoriaClinicaRepository repository;

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