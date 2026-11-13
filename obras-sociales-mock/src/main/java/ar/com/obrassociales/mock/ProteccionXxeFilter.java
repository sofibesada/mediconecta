package ar.com.obrassociales.mock;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Proteccion XXE (XML External Entity) del servicio SOAP, explicita y ANTES del parser.
 *
 * Un mensaje con DOCTYPE puede declarar entidades externas (leer archivos del servidor,
 * hacer pedidos a la red interna) o entidades anidadas que multiplican el tamaño
 * ("billion laughs"). SOAP no usa DTD, asi que cualquier DOCTYPE o ENTITY se rechaza con
 * un SOAP Fault, sin llegar al parser. El parser de CXF tambien rechaza DTD por defecto;
 * este filtro no depende de esa configuracion y devuelve un Fault sin detalles internos.
 *
 * Tambien limita el tamaño del mensaje (1 MB): un SOAP de este servicio ocupa pocos KB.
 */
@WebFilter(urlPatterns = "/ObrasSociales")
public class ProteccionXxeFilter extends HttpFilter {

    private static final Logger LOG = Logger.getLogger(ProteccionXxeFilter.class.getName());
    static final int TAMANO_MAXIMO = 1024 * 1024;

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (!"POST".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, res); // GET ?wsdl
            return;
        }
        byte[] cuerpo = leer(req.getInputStream());
        if (cuerpo == null) {
            fault(res, "Mensaje rechazado: supera el tamaño máximo permitido (1 MB)");
            return;
        }
        if (tieneDtd(cuerpo)) {
            LOG.warning("[XXE] Mensaje SOAP rechazado: incluye DOCTYPE o ENTITY (origen " + req.getRemoteAddr() + ")");
            fault(res, "Mensaje rechazado: el XML no puede incluir DOCTYPE ni entidades (protección XXE)");
            return;
        }
        chain.doFilter(new ConCuerpo(req, cuerpo), res);
    }

    /** true si el XML declara un DTD o entidades. Se busca sin distinguir mayúsculas. */
    static boolean tieneDtd(byte[] cuerpo) {
        String texto = new String(cuerpo, StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
        return texto.contains("<!DOCTYPE") || texto.contains("<!ENTITY");
    }

    /** Lee el cuerpo completo; null si supera el tamaño máximo. */
    static byte[] leer(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
            if (out.size() > TAMANO_MAXIMO) {
                return null;
            }
        }
        return out.toByteArray();
    }

    /** SOAP 1.1: un Fault se responde con HTTP 500 y faultcode Client (el error es del mensaje). */
    private static void fault(HttpServletResponse res, String motivo) throws IOException {
        res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        res.setContentType("text/xml; charset=UTF-8");
        res.getWriter().write("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + "<soap:Fault><faultcode>soap:Client</faultcode><faultstring>" + motivo + "</faultstring></soap:Fault>"
                + "</soap:Body></soap:Envelope>");
    }

    /** El pedido con el cuerpo ya leído, para que el servicio SOAP lo vuelva a leer. */
    private static final class ConCuerpo extends HttpServletRequestWrapper {
        private final byte[] cuerpo;

        ConCuerpo(HttpServletRequest req, byte[] cuerpo) {
            super(req);
            this.cuerpo = cuerpo;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(cuerpo);
            return new ServletInputStream() {
                @Override public int read() { return in.read(); }
                @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return cuerpo.length;
        }

        @Override
        public long getContentLengthLong() {
            return cuerpo.length;
        }
    }
}
