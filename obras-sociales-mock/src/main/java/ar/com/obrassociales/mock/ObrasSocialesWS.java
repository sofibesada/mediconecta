package ar.com.obrassociales.mock;

import ar.com.obrassociales.mock.Contrato.ObraSocial;
import ar.com.obrassociales.mock.Contrato.ResultadoAutorizacion;
import ar.com.obrassociales.mock.Contrato.ResultadoCobertura;
import ar.com.obrassociales.mock.Contrato.ResultadoReclamo;
import ar.com.obrassociales.mock.Padron.Plan;
import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Servicio SOAP del sistema legado de obras sociales (simulado).
 *
 * Contrato WSDL (document/literal): http://localhost:8080/obras-sociales-mock/ObrasSociales?wsdl
 *
 *   listarObrasSociales   obras sociales y prepagas con las que opera
 *   consultarCobertura    si el afiliado esta cubierto, con que plan y que copago
 *   autorizarPrestacion   autoriza una consulta y devuelve el codigo de autorizacion
 *   registrarReclamo      factura (reclama) una prestacion autorizada
 *
 * Para simular fallas del legado (system properties de WildFly):
 *   obrassociales.mock.fallar=true    registrarReclamo responde con SOAP Fault
 *   obrassociales.mock.demoraMs=7000  tarda N ms en responder (provoca timeout del cliente)
 */
@WebService(name = "ObrasSocialesPortType", serviceName = "ObrasSocialesService",
        portName = "ObrasSocialesPort", targetNamespace = Contrato.NS)
public class ObrasSocialesWS {

    private static final Logger LOG = Logger.getLogger(ObrasSocialesWS.class.getName());

    @WebMethod
    @WebResult(name = "obraSocial")
    public List<ObraSocial> listarObrasSociales() {
        demorar();
        return Padron.obrasSociales();
    }

    @WebMethod
    @WebResult(name = "resultado")
    public ResultadoCobertura consultarCobertura(@WebParam(name = "obraSocial") String obraSocial,
                                                 @WebParam(name = "numeroAfiliado") String numeroAfiliado) {
        demorar();
        ResultadoCobertura r = new ResultadoCobertura();
        Optional<Plan> plan = Padron.plan(obraSocial);
        if (plan.isEmpty()) {
            r.motivo = "Obra social inexistente";
        } else if (!Padron.afiliadoValido(numeroAfiliado)) {
            r.motivo = "Numero de afiliado invalido: deben ser 8 digitos";
        } else if (Padron.dadoDeBaja(numeroAfiliado)) {
            r.motivo = "Afiliado dado de baja";
        } else {
            r.cubierto = true;
            r.plan = plan.get().plan();
            r.copagoPorcentaje = plan.get().copagoPorcentaje();
        }
        LOG.info("[Mock obras sociales] consultarCobertura " + obraSocial + " afiliado " + enmascarar(numeroAfiliado)
                + " -> " + (r.cubierto ? "cubierto, " + r.plan : r.motivo));
        return r;
    }

    @WebMethod
    @WebResult(name = "resultado")
    public ResultadoAutorizacion autorizarPrestacion(@WebParam(name = "obraSocial") String obraSocial,
                                                     @WebParam(name = "numeroAfiliado") String numeroAfiliado,
                                                     @WebParam(name = "codigoPrestacion") String codigoPrestacion,
                                                     @WebParam(name = "fechaPrestacion") String fechaPrestacion,
                                                     @WebParam(name = "referencia") String referencia) {
        demorar();
        if (referencia == null || referencia.isBlank()) {
            throw new IllegalArgumentException("La referencia es obligatoria");
        }
        ResultadoAutorizacion r = Padron.AUTORIZACIONES.computeIfAbsent(referencia, ref -> {
            ResultadoAutorizacion nueva = new ResultadoAutorizacion();
            ResultadoCobertura cobertura = consultarCobertura(obraSocial, numeroAfiliado);
            if (!cobertura.cubierto) {
                nueva.motivo = cobertura.motivo;
            } else if (Padron.prestacionNoCubierta(numeroAfiliado)) {
                nueva.motivo = "La prestacion " + codigoPrestacion + " supera el tope mensual del plan";
            } else {
                nueva.autorizada = true;
                nueva.codigoAutorizacion = "AUT-" + Padron.SECUENCIA.incrementAndGet();
            }
            return nueva;
        });
        LOG.info("[Mock obras sociales] autorizarPrestacion ref=" + referencia + " " + obraSocial + " afiliado "
                + enmascarar(numeroAfiliado) + " -> " + (r.autorizada ? r.codigoAutorizacion : "RECHAZADA: " + r.motivo));
        return r;
    }

    @WebMethod
    @WebResult(name = "resultado")
    public ResultadoReclamo registrarReclamo(@WebParam(name = "obraSocial") String obraSocial,
                                             @WebParam(name = "numeroAfiliado") String numeroAfiliado,
                                             @WebParam(name = "codigoAutorizacion") String codigoAutorizacion,
                                             @WebParam(name = "importe") BigDecimal importe,
                                             @WebParam(name = "referencia") String referencia) {
        demorar();
        if (Boolean.getBoolean("obrassociales.mock.fallar")) {
            LOG.warning("[Mock obras sociales] registrarReclamo ref=" + referencia + " -> FALLA simulada");
            throw new IllegalStateException("Sistema de facturacion fuera de servicio (falla simulada)");
        }
        ResultadoReclamo r = Padron.RECLAMOS.computeIfAbsent(referencia, ref -> {
            ResultadoReclamo nuevo = new ResultadoReclamo();
            if (codigoAutorizacion == null || codigoAutorizacion.isBlank()) {
                nuevo.motivo = "Falta el codigo de autorizacion";
            } else {
                nuevo.aceptado = true;
                nuevo.numeroReclamo = "REC-" + Padron.SECUENCIA.incrementAndGet();
                nuevo.importeReconocido = importe;
            }
            return nuevo;
        });
        LOG.info("[Mock obras sociales] registrarReclamo ref=" + referencia + " " + obraSocial + " $" + importe
                + " -> " + (r.aceptado ? r.numeroReclamo : "RECHAZADO: " + r.motivo));
        return r;
    }

    private static void demorar() {
        long ms = Long.getLong("obrassociales.mock.demoraMs", 0L);
        if (ms > 0) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String enmascarar(String numero) {
        return numero == null || numero.length() < 4 ? "****" : "****" + numero.substring(numero.length() - 4);
    }
}
