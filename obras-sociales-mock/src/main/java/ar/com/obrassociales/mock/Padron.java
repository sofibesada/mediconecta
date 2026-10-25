package ar.com.obrassociales.mock;

import ar.com.obrassociales.mock.Contrato.ObraSocial;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Padron del sistema legado: obras sociales con su plan, afiliados y vademecum
 * (medicamentos con descuento por obra social).
 *
 * Se administra desde el panel web del legado (admin.html) y se guarda en un archivo
 * JSON en la carpeta de datos de WildFly, asi no se pierde al redesplegar. La primera
 * vez se carga con datos de prueba.
 *
 * Las autorizaciones y los reclamos se guardan por "referencia" (la manda MediConecta):
 * si el mismo pedido llega dos veces, se devuelve el mismo resultado (idempotencia).
 */
public final class Padron {

    private static final Logger LOG = Logger.getLogger(Padron.class.getName());
    private static final Jsonb JSONB = JsonbBuilder.create(new JsonbConfig().withFormatting(true));

    /** Una obra social o prepaga con su plan (el legado simplifica a un plan por entidad). */
    public static class Entidad {
        public String codigo;
        public String nombre;
        public String tipo;          // OBRA_SOCIAL o PREPAGA
        public String plan;
        public int copagoPorcentaje; // lo que paga el afiliado de cada consulta
    }

    public static class Afiliado {
        public String obraSocial;
        public String numero;
        public String nombre;
        public boolean activo = true;
        /** true: alcanzo el tope de consultas del plan, las autorizaciones se rechazan. */
        public boolean topeAlcanzado;
    }

    public static class Medicamento {
        public String obraSocial;
        public String nombre;
        public int descuentoPorcentaje;
    }

    public static class Datos {
        public List<Entidad> obrasSociales = new ArrayList<>();
        public List<Afiliado> afiliados = new ArrayList<>();
        public List<Medicamento> vademecum = new ArrayList<>();
    }

    static final Map<String, Contrato.ResultadoAutorizacion> AUTORIZACIONES = new ConcurrentHashMap<>();
    static final Map<String, Contrato.ResultadoReclamo> RECLAMOS = new ConcurrentHashMap<>();
    static final AtomicLong SECUENCIA = new AtomicLong(1000);

    private static Datos datos;

    private Padron() {}

    // ------------------------------------------------------------------ consultas

    static synchronized Datos datos() {
        if (datos == null) {
            datos = cargar();
        }
        return datos;
    }

    static synchronized List<ObraSocial> obrasSociales() {
        return datos().obrasSociales.stream()
                .sorted(Comparator.comparing(e -> e.nombre))
                .map(e -> new ObraSocial(e.codigo, e.nombre, e.tipo))
                .toList();
    }

    static synchronized Optional<Entidad> entidad(String codigo) {
        return datos().obrasSociales.stream().filter(e -> e.codigo.equals(codigo)).findFirst();
    }

    static synchronized Optional<Afiliado> afiliado(String obraSocial, String numero) {
        return datos().afiliados.stream()
                .filter(a -> a.obraSocial.equals(obraSocial) && a.numero.equals(numero))
                .findFirst();
    }

    /** Descuento del medicamento recetado (se busca el nombre del vademecum dentro del texto de la receta). */
    static synchronized Optional<Medicamento> medicamento(String obraSocial, String recetado) {
        String texto = normalizar(recetado);
        return datos().vademecum.stream()
                .filter(m -> m.obraSocial.equals(obraSocial) && texto.contains(normalizar(m.nombre)))
                .max(Comparator.comparingInt(m -> m.descuentoPorcentaje));
    }

    // ------------------------------------------------------------------ administracion

    static synchronized void guardarEntidad(Entidad e) {
        validar(e.codigo != null && e.codigo.matches("[A-Z0-9]{2,15}"), "Codigo: de 2 a 15 letras mayusculas o numeros");
        validar(e.nombre != null && !e.nombre.isBlank(), "El nombre es obligatorio");
        validar(e.copagoPorcentaje >= 0 && e.copagoPorcentaje <= 100, "El copago va de 0 a 100");
        e.tipo = "PREPAGA".equals(e.tipo) ? "PREPAGA" : "OBRA_SOCIAL";
        datos().obrasSociales.removeIf(x -> x.codigo.equals(e.codigo));
        datos().obrasSociales.add(e);
        guardar();
    }

    static synchronized void borrarEntidad(String codigo) {
        datos().obrasSociales.removeIf(x -> x.codigo.equals(codigo));
        datos().afiliados.removeIf(x -> x.obraSocial.equals(codigo));
        datos().vademecum.removeIf(x -> x.obraSocial.equals(codigo));
        guardar();
    }

    static synchronized void guardarAfiliado(Afiliado a) {
        validar(entidad(a.obraSocial).isPresent(), "La obra social no existe");
        validar(a.numero != null && a.numero.matches("\\d{8}"), "El numero de afiliado debe tener 8 digitos");
        validar(a.nombre != null && !a.nombre.isBlank(), "El nombre del afiliado es obligatorio");
        datos().afiliados.removeIf(x -> x.obraSocial.equals(a.obraSocial) && x.numero.equals(a.numero));
        datos().afiliados.add(a);
        guardar();
    }

    static synchronized void borrarAfiliado(String obraSocial, String numero) {
        datos().afiliados.removeIf(x -> x.obraSocial.equals(obraSocial) && x.numero.equals(numero));
        guardar();
    }

    static synchronized void guardarMedicamento(Medicamento m) {
        validar(entidad(m.obraSocial).isPresent(), "La obra social no existe");
        validar(m.nombre != null && !m.nombre.isBlank(), "El nombre del medicamento es obligatorio");
        validar(m.descuentoPorcentaje > 0 && m.descuentoPorcentaje <= 100, "El descuento va de 1 a 100");
        m.nombre = m.nombre.trim();
        datos().vademecum.removeIf(x -> x.obraSocial.equals(m.obraSocial) && normalizar(x.nombre).equals(normalizar(m.nombre)));
        datos().vademecum.add(m);
        guardar();
    }

    static synchronized void borrarMedicamento(String obraSocial, String nombre) {
        datos().vademecum.removeIf(x -> x.obraSocial.equals(obraSocial) && normalizar(x.nombre).equals(normalizar(nombre)));
        guardar();
    }

    // ------------------------------------------------------------------ persistencia

    private static Path archivo() {
        String base = System.getProperty("jboss.server.data.dir", System.getProperty("java.io.tmpdir"));
        return Path.of(base, "obras-sociales-mock", "padron.json");
    }

    private static Datos cargar() {
        Path f = archivo();
        try {
            if (Files.exists(f)) {
                LOG.info("[Mock obras sociales] padron cargado de " + f);
                return JSONB.fromJson(Files.readString(f, StandardCharsets.UTF_8), Datos.class);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warning("[Mock obras sociales] no se pudo leer " + f + ", se usan los datos de prueba: " + e.getMessage());
        }
        datos = datosDePrueba();
        guardar();
        return datos;
    }

    private static void guardar() {
        Path f = archivo();
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, JSONB.toJson(datos), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el padron en " + f, e);
        }
    }

    private static Datos datosDePrueba() {
        Datos d = new Datos();
        d.obrasSociales.add(entidad("OSMED", "Obra Social Medica Nacional", "OBRA_SOCIAL", "OSMED 210", 30));
        d.obrasSociales.add(entidad("PREVENIR", "Prevenir Salud", "OBRA_SOCIAL", "Prevenir Clasico", 20));
        d.obrasSociales.add(entidad("SALUDPLUS", "Salud Plus Prepaga", "PREPAGA", "Plus 410", 0));
        d.afiliados.add(afiliado("OSMED", "12345678", "Afiliado de prueba OSMED", true, false));
        d.afiliados.add(afiliado("OSMED", "12345679", "Afiliado dado de baja", false, false));
        d.afiliados.add(afiliado("PREVENIR", "12345677", "Afiliado con tope alcanzado", true, true));
        d.afiliados.add(afiliado("PREVENIR", "22223333", "Afiliado de prueba Prevenir", true, false));
        d.afiliados.add(afiliado("SALUDPLUS", "33334444", "Afiliado de prueba Salud Plus", true, false));
        d.vademecum.add(medicamento("OSMED", "Enalapril", 40));
        d.vademecum.add(medicamento("OSMED", "Amoxicilina", 50));
        d.vademecum.add(medicamento("OSMED", "Ibuprofeno", 30));
        d.vademecum.add(medicamento("PREVENIR", "Enalapril", 70));
        d.vademecum.add(medicamento("PREVENIR", "Metformina", 60));
        d.vademecum.add(medicamento("SALUDPLUS", "Amoxicilina", 100));
        d.vademecum.add(medicamento("SALUDPLUS", "Omeprazol", 40));
        d.vademecum.add(medicamento("SALUDPLUS", "Atorvastatina", 60));
        return d;
    }

    private static Entidad entidad(String codigo, String nombre, String tipo, String plan, int copago) {
        Entidad e = new Entidad();
        e.codigo = codigo; e.nombre = nombre; e.tipo = tipo; e.plan = plan; e.copagoPorcentaje = copago;
        return e;
    }

    private static Afiliado afiliado(String os, String numero, String nombre, boolean activo, boolean tope) {
        Afiliado a = new Afiliado();
        a.obraSocial = os; a.numero = numero; a.nombre = nombre; a.activo = activo; a.topeAlcanzado = tope;
        return a;
    }

    private static Medicamento medicamento(String os, String nombre, int descuento) {
        Medicamento m = new Medicamento();
        m.obraSocial = os; m.nombre = nombre; m.descuentoPorcentaje = descuento;
        return m;
    }

    private static String normalizar(String s) {
        return s == null ? "" : java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private static void validar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new IllegalArgumentException(mensaje);
        }
    }
}
