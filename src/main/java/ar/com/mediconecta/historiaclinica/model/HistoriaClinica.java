package ar.com.mediconecta.historiaclinica.model;

import java.util.List;

public class HistoriaClinica {
    private Long pacienteId;
    private List<Diagnostico> diagnosticos;
    private List<Receta> recetas;

    public HistoriaClinica() {}

    public HistoriaClinica(Long pacienteId, List<Diagnostico> diagnosticos, List<Receta> recetas) {
        this.pacienteId = pacienteId;
        this.diagnosticos = diagnosticos;
        this.recetas = recetas;
    }

    public Long getPacienteId() { return pacienteId; }
    public void setPacienteId(Long pacienteId) { this.pacienteId = pacienteId; }
    public List<Diagnostico> getDiagnosticos() { return diagnosticos; }
    public void setDiagnosticos(List<Diagnostico> diagnosticos) { this.diagnosticos = diagnosticos; }
    public List<Receta> getRecetas() { return recetas; }
    public void setRecetas(List<Receta> recetas) { this.recetas = recetas; }
}
