package ar.com.mediconecta.obrassociales.integracion;

import ar.com.mediconecta.obrassociales.integracion.ContratoLegado.CoberturaMedicamento;
import ar.com.mediconecta.obrassociales.integracion.ContratoLegado.ObraSocial;
import ar.com.mediconecta.obrassociales.integracion.ContratoLegado.ResultadoAutorizacion;
import ar.com.mediconecta.obrassociales.integracion.ContratoLegado.ResultadoCobertura;
import ar.com.mediconecta.obrassociales.integracion.ContratoLegado.ResultadoReclamo;
import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import java.math.BigDecimal;
import java.util.List;

/**
 * Interfaz del puerto SOAP (portType "ObrasSocialesPortType" del WSDL). JAX-WS arma
 * un proxy que traduce cada llamada a un mensaje SOAP document/literal.
 */
@WebService(name = "ObrasSocialesPortType", targetNamespace = ContratoLegado.NS)
public interface ObrasSocialesPortType {

    @WebMethod
    @WebResult(name = "obraSocial")
    List<ObraSocial> listarObrasSociales();

    @WebMethod
    @WebResult(name = "resultado")
    ResultadoCobertura consultarCobertura(@WebParam(name = "obraSocial") String obraSocial,
                                          @WebParam(name = "numeroAfiliado") String numeroAfiliado);

    @WebMethod
    @WebResult(name = "resultado")
    ResultadoAutorizacion autorizarPrestacion(@WebParam(name = "obraSocial") String obraSocial,
                                              @WebParam(name = "numeroAfiliado") String numeroAfiliado,
                                              @WebParam(name = "codigoPrestacion") String codigoPrestacion,
                                              @WebParam(name = "fechaPrestacion") String fechaPrestacion,
                                              @WebParam(name = "referencia") String referencia);

    @WebMethod
    @WebResult(name = "cobertura")
    List<CoberturaMedicamento> consultarCoberturaMedicamentos(@WebParam(name = "obraSocial") String obraSocial,
                                                              @WebParam(name = "numeroAfiliado") String numeroAfiliado,
                                                              @WebParam(name = "medicamento") List<String> medicamentos);

    @WebMethod
    @WebResult(name = "resultado")
    ResultadoReclamo registrarReclamo(@WebParam(name = "obraSocial") String obraSocial,
                                      @WebParam(name = "numeroAfiliado") String numeroAfiliado,
                                      @WebParam(name = "codigoAutorizacion") String codigoAutorizacion,
                                      @WebParam(name = "importe") BigDecimal importe,
                                      @WebParam(name = "referencia") String referencia);
}
