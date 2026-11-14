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

    /** Una obra social o prepaga con su plan basico (cada afiliado puede tener otro plan). */
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
        /** Plan del afiliado (por ejemplo, OSDE 410). Si es null, se usa el plan de la entidad. */
        public String plan;
        /** Copago del plan del afiliado. Si es null, se usa el de la entidad. */
        public Integer copagoPorcentaje;
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
        validar(a.numero != null && a.numero.matches("\\d{6,14}"), "El numero de afiliado debe tener entre 6 y 14 digitos");
        validar(a.copagoPorcentaje == null || (a.copagoPorcentaje >= 0 && a.copagoPorcentaje <= 100), "El copago va de 0 a 100");
        if (a.plan != null && a.plan.isBlank()) {
            a.plan = null;
        }
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

    /**
     * Datos iniciales: obras sociales y prepagas reales de Argentina. Los planes son los nombres
     * comerciales de cada entidad; los porcentajes de copago, los afiliados y los descuentos son
     * de ejemplo (el padron real de cada entidad no es publico).
     */
    private static Datos datosDePrueba() {
        Datos d = new Datos();
        d.obrasSociales.add(entidad("OSDE", "OSDE", "PREPAGA", "OSDE 210", 30));
        d.obrasSociales.add(entidad("SWISS", "Swiss Medical", "PREPAGA", "SMG20", 25));
        d.obrasSociales.add(entidad("GALENO", "Galeno", "PREPAGA", "Galeno Azul 220", 25));
        d.obrasSociales.add(entidad("MEDIFE", "Medifé", "PREPAGA", "Medifé Bronce", 30));
        d.obrasSociales.add(entidad("OMINT", "OMINT", "PREPAGA", "OMINT Clásico", 20));
        d.obrasSociales.add(entidad("SANCOR", "SanCor Salud", "PREPAGA", "SanCor 1000", 30));
        d.obrasSociales.add(entidad("IOMA", "IOMA (Instituto de Obra Médico Asistencial)", "OBRA_SOCIAL", "IOMA", 30));
        d.obrasSociales.add(entidad("PAMI", "PAMI", "OBRA_SOCIAL", "PAMI", 0));
        d.obrasSociales.add(entidad("OSECAC", "OSECAC (Empleados de Comercio)", "OBRA_SOCIAL", "OSECAC", 30));
        d.obrasSociales.add(entidad("OSDEPYM", "OSDEPYM", "OBRA_SOCIAL", "OSDEPYM 2000", 30));

        // Afiliados de prueba (documentados en el README). El plan del afiliado manda sobre el de la entidad.
        d.afiliados.add(afiliado("OSDE", "12345678", "Afiliada de prueba OSDE", true, false, "OSDE 210", 30));
        d.afiliados.add(afiliado("OSDE", "12345679", "Afiliado dado de baja", false, false, null, null));
        d.afiliados.add(afiliado("OSDE", "61234567", "Afiliado OSDE 410", true, false, "OSDE 410", 10));
        d.afiliados.add(afiliado("SWISS", "12345677", "Afiliado con tope alcanzado", true, true, "SMG30", 20));
        d.afiliados.add(afiliado("SWISS", "22223333", "Afiliada de prueba Swiss Medical", true, false, "SMG40", 15));
        d.afiliados.add(afiliado("GALENO", "33334444", "Afiliado de prueba Galeno", true, false, "Galeno Plata 330", 20));
        d.afiliados.add(afiliado("MEDIFE", "44445555", "Afiliada de prueba Medifé", true, false, "Medifé Plata", 25));
        d.afiliados.add(afiliado("PAMI", "15012345678900", "Jubilado de prueba PAMI", true, false, null, null));
        d.afiliados.add(afiliado("IOMA", "66667777", "Afiliada de prueba IOMA", true, false, null, null));
        d.afiliados.add(afiliado("OSECAC", "77778888", "Afiliado de prueba OSECAC", true, false, null, null));
        d.afiliados.add(afiliado("SANCOR", "88889999", "Afiliada de prueba SanCor", true, false, "SanCor 3000", 20));
        d.afiliados.add(afiliado("OMINT", "99990000", "Afiliado de prueba OMINT", true, false, null, null));
        d.afiliados.add(afiliado("OSDEPYM", "11112222", "Afiliada de prueba OSDEPYM", true, false, null, null));

        d.vademecum.add(medicamento("OSDE", "Enalapril", 40));
        d.vademecum.add(medicamento("OSDE", "Amoxicilina", 50));
        d.vademecum.add(medicamento("OSDE", "Ibuprofeno", 30));
        d.vademecum.add(medicamento("OSDE", "Losartán", 40));
        d.vademecum.add(medicamento("SWISS", "Enalapril", 70));
        d.vademecum.add(medicamento("SWISS", "Metformina", 60));
        d.vademecum.add(medicamento("GALENO", "Amoxicilina", 40));
        d.vademecum.add(medicamento("MEDIFE", "Omeprazol", 40));
        d.vademecum.add(medicamento("PAMI", "Enalapril", 100));
        d.vademecum.add(medicamento("PAMI", "Losartán", 100));
        d.vademecum.add(medicamento("PAMI", "Metformina", 100));
        d.vademecum.add(medicamento("PAMI", "Atorvastatina", 80));
        d.vademecum.add(medicamento("IOMA", "Ibuprofeno", 50));
        d.vademecum.add(medicamento("SANCOR", "Atorvastatina", 60));
        return d;
    }

    private static Entidad entidad(String codigo, String nombre, String tipo, String plan, int copago) {
        Entidad e = new Entidad();
        e.codigo = codigo; e.nombre = nombre; e.tipo = tipo; e.plan = plan; e.copagoPorcentaje = copago;
        return e;
    }

    private static Afiliado afiliado(String os, String numero, String nombre, boolean activo, boolean tope,
                                     String plan, Integer copago) {
        Afiliado a = new Afiliado();
        a.obraSocial = os; a.numero = numero; a.nombre = nombre; a.activo = activo; a.topeAlcanzado = tope;
        a.plan = plan; a.copagoPorcentaje = copago;
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
