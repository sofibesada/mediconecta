package ar.com.mediconecta.obrassociales.business;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que MediConecta necesita de las obras sociales, en terminos del dominio. Quien la
 * usa no sabe que del otro lado hay un sistema legado SOAP (patron Adapter).
 */
public interface IObraSocialService {

    /** Codigo de la prestacion "consulta medica" que se pide autorizar y se factura. */
    String PRESTACION_CONSULTA = "420101";

    record ObraSocialDisponible(String codigo, String nombre, String tipo) {}

    record CoberturaVerificada(boolean cubierta, String plan, int copagoPorcentaje, String motivo) {}

    record ResultadoAutorizacion(boolean autorizada, String codigo, String motivo) {}

    /** Descuento de un medicamento recetado segun el vademecum de la obra social (0 = sin cobertura). */
    record CoberturaMedicamento(String medicamento, boolean cubierto, int descuentoPorcentaje) {}

    record ResultadoReclamo(boolean aceptado, String numero, BigDecimal importeReconocido, String motivo) {}

    List<ObraSocialDisponible> listarObrasSociales();

    CoberturaVerificada verificarCobertura(String obraSocial, String numeroAfiliado);

    ResultadoAutorizacion autorizarConsulta(String obraSocial, String numeroAfiliado, Long turnoId, LocalDateTime fecha);

    List<CoberturaMedicamento> coberturaMedicamentos(String obraSocial, String numeroAfiliado, List<String> medicamentos);

    ResultadoReclamo registrarReclamo(String obraSocial, String numeroAfiliado, String codigoAutorizacion,
                                      BigDecimal importe, Long turnoId);
}
