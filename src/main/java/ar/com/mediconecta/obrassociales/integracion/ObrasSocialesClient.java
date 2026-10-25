package ar.com.mediconecta.obrassociales.integracion;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.WebServiceException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import javax.xml.namespace.QName;

/**
 * Cliente SOAP (JAX-WS) del sistema legado de obras sociales.
 * Integracion SINCRONICA: espera la respuesta, con timeouts para no colgar el hilo.
 *
 * El Service se arma a partir del WSDL publicado por el legado (contrato primero) y se
 * reutiliza; cada llamada pide un puerto nuevo, porque el contexto del puerto no se
 * comparte entre hilos. Cualquier falla de red, timeout o SOAP Fault se traduce a
 * ObrasSocialesNoDisponibleException.
 *
 * Configuracion (system property de WildFly):
 *   mediconecta.obrassociales.url  endpoint del legado (por defecto, el simulador local)
 */
@ApplicationScoped
public class ObrasSocialesClient {

    private static final Logger LOG = Logger.getLogger(ObrasSocialesClient.class.getName());
    private static final String URL_POR_DEFECTO = "http://localhost:8080/obras-sociales-mock/ObrasSociales";
    private static final QName SERVICIO = new QName(ContratoLegado.NS, "ObrasSocialesService");
    private static final QName PUERTO = new QName(ContratoLegado.NS, "ObrasSocialesPort");
    private static final int TIMEOUT_CONEXION_MS = 2000;
    private static final int TIMEOUT_LECTURA_MS = 5000;

    private volatile Service servicio;

    public <T> T llamar(String operacion, Function<ObrasSocialesPortType, T> llamada) {
        try {
            T resultado = llamada.apply(puerto());
            LOG.info("[SOAP legado] " + operacion + " -> respuesta recibida");
            return resultado;
        } catch (WebServiceException e) {
            servicio = null; // si el legado se cayo, en la proxima llamada se vuelve a leer el WSDL
            LOG.warning("[SOAP legado] " + operacion + " -> sin respuesta: " + e.getMessage());
            throw new ObrasSocialesNoDisponibleException(
                    "El sistema de la obra social no responde (" + operacion + ")", e);
        }
    }

    private ObrasSocialesPortType puerto() {
        Service s = servicio;
        if (s == null) {
            synchronized (this) {
                s = servicio;
                if (s == null) {
                    s = Service.create(wsdl(), SERVICIO);
                    servicio = s;
                }
            }
        }
        ObrasSocialesPortType puerto = s.getPort(PUERTO, ObrasSocialesPortType.class);
        Map<String, Object> contexto = ((BindingProvider) puerto).getRequestContext();
        contexto.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, url());
        // Nombres que entienden las implementaciones de JAX-WS (CXF en WildFly incluido).
        contexto.put("jakarta.xml.ws.client.connectionTimeout", TIMEOUT_CONEXION_MS);
        contexto.put("jakarta.xml.ws.client.receiveTimeout", TIMEOUT_LECTURA_MS);
        contexto.put("javax.xml.ws.client.connectionTimeout", TIMEOUT_CONEXION_MS);
        contexto.put("javax.xml.ws.client.receiveTimeout", TIMEOUT_LECTURA_MS);
        return puerto;
    }

    private URL wsdl() {
        try {
            return new URL(url() + "?wsdl");
        } catch (MalformedURLException e) {
            throw new IllegalStateException("URL del sistema de obras sociales invalida: " + url(), e);
        }
    }

    private String url() {
        return System.getProperty("mediconecta.obrassociales.url", URL_POR_DEFECTO);
    }
}
