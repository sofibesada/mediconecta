package ar.com.mediconecta.historiaclinica.presentation;

import java.util.List;

/**
 * Lo que ve el profesional (o el propio paciente) al abrir la historia clinica: datos del
 * paciente, su cobertura y, por cada receta, si tiene descuento con su obra social.
 */
public class FichaPacienteResponse {

    public Long id;
    public String nombre;
    public String email;
    public String obraSocial;
    public String planObraSocial;
    public String numeroAfiliado;
    public Integer copagoPorcentaje;
    /** Si no es null, no se pudo consultar el vademecum de la obra social. */
    public String avisoCobertura;
    public List<RecetaCobertura> recetas;

    public static class RecetaCobertura {
        public Long recetaId;
        public String medicamento;
        public boolean cubierto;
        public int descuentoPorcentaje;
    }
}
