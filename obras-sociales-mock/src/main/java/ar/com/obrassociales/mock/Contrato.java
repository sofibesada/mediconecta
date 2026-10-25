package ar.com.obrassociales.mock;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;
import java.math.BigDecimal;

/**
 * Tipos del contrato SOAP (aparecen en el XML Schema del WSDL). Todos en el mismo
 * namespace que el servicio, con acceso por campo.
 */
public final class Contrato {

    public static final String NS = "http://legado.obrassociales.ar/ws";

    private Contrato() {}

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "obraSocial", namespace = NS)
    public static class ObraSocial {
        public String codigo;
        public String nombre;
        /** OBRA_SOCIAL o PREPAGA. */
        public String tipo;

        public ObraSocial() {}

        public ObraSocial(String codigo, String nombre, String tipo) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.tipo = tipo;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "resultadoCobertura", namespace = NS)
    public static class ResultadoCobertura {
        public boolean cubierto;
        public String plan;
        /** Porcentaje del valor de la consulta que paga el afiliado. */
        public int copagoPorcentaje;
        public String motivo;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "coberturaMedicamento", namespace = NS)
    public static class CoberturaMedicamento {
        /** El medicamento tal como vino en el pedido. */
        public String medicamento;
        public boolean cubierto;
        public int descuentoPorcentaje;
        /** Nombre del vademecum con el que coincidio. */
        public String vademecum;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "resultadoAutorizacion", namespace = NS)
    public static class ResultadoAutorizacion {
        public boolean autorizada;
        public String codigoAutorizacion;
        public String motivo;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "resultadoReclamo", namespace = NS)
    public static class ResultadoReclamo {
        public boolean aceptado;
        public String numeroReclamo;
        public BigDecimal importeReconocido;
        public String motivo;
    }
}
