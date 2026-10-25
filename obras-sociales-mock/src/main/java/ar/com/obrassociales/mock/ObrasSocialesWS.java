package ar.com.obrassociales.mock;

import ar.com.obrassociales.mock.Contrato.ObraSocial;
import ar.com.obrassociales.mock.Contrato.ResultadoAutorizacion;
import ar.com.obrassociales.mock.Contrato.ResultadoCobertura;
import ar.com.obrassociales.mock.Contrato.ResultadoReclamo;
import ar.com.obrassociales.mock.Contrato.CoberturaMedicamento;
import ar.com.obrassociales.mock.Padron.Afiliado;
import ar.com.obrassociales.mock.Padron.Entidad;
import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import java.math.BigDecimal;
import java.util.ArrayList;
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
 *   consultarCoberturaMedicamentos  descuento de cada medicamento segun el vademecum
 *
 * Los datos (obras sociales, afiliados, vademecum) se administran desde admin.html.
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
        Optional<Entidad> entidad = Padron.entidad(obraSocial);
        Optional<Afiliado> afiliado = Padron.afiliado(obraSocial, numeroAfiliado);
        if (entidad.isEmpty()) {
            r.motivo = "Obra social inexistente";
        } else if (afiliado.isEmpty()) {
            r.motivo = "El afiliado no figura en el padron";
        } else if (!afiliado.get().activo) {
            r.motivo = "Afiliado dado de baja";
        } else {
            r.cubierto = true;
            r.plan = entidad.get().plan;
            r.copagoPorcentaje = entidad.get().copagoPorcentaje;
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
            } else if (Padron.afiliado(obraSocial, numeroAfiliado).map(a -> a.topeAlcanzado).orElse(false)) {
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
    @WebResult(name = "cobertura")
    public List<CoberturaMedicamento> consultarCoberturaMedicamentos(@WebParam(name = "obraSocial") String obraSocial,
                                                                     @WebParam(name = "numeroAfiliado") String numeroAfiliado,
                                                                     @WebParam(name = "medicamento") List<String> medicamentos) {
        demorar();
        boolean vigente = consultarCobertura(obraSocial, numeroAfiliado).cubierto;
        List<CoberturaMedicamento> resultado = new ArrayList<>();
        for (String recetado : medicamentos == null ? List.<String>of() : medicamentos) {
            CoberturaMedicamento c = new CoberturaMedicamento();
            c.medicamento = recetado;
            if (vigente) {
                Padron.medicamento(obraSocial, recetado).ifPresent(m -> {
                    c.cubierto = true;
                    c.descuentoPorcentaje = m.descuentoPorcentaje;
                    c.vademecum = m.nombre;
                });
            }
            resultado.add(c);
        }
        LOG.info("[Mock obras sociales] consultarCoberturaMedicamentos " + obraSocial + " afiliado " + enmascarar(numeroAfiliado)
                + " -> " + resultado.stream().filter(c -> c.cubierto).count() + " de " + resultado.size() + " con descuento");
        return resultado;
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
