package ar.com.mediconecta.historiaclinica.business;

import ar.com.mediconecta.historiaclinica.model.Diagnostico;
import ar.com.mediconecta.historiaclinica.model.HistoriaClinica;
import ar.com.mediconecta.historiaclinica.model.Receta;

public interface IHistoriaClinicaService {
    HistoriaClinica consultarHistoria(Long pacienteId);
    void registrarDiagnostico(Long pacienteId, Diagnostico diagnostico);
    void registrarReceta(Long pacienteId, Receta receta);
}