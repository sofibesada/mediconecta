package ar.com.mediconecta.obrassociales.integracion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;
import java.math.BigDecimal;

/**
 * Tipos del contrato SOAP del sistema legado de obras sociales, tal como los define su
 * WSDL (mismo namespace y nombres de tipo). Solo los usa el cliente: el resto de
 * MediConecta trabaja con los tipos del dominio que arma ObraSocialService.
 */
public final class ContratoLegado {

    public static final String NS = "http://legado.obrassociales.ar/ws";

    private ContratoLegado() {}

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "obraSocial", namespace = NS)
    public static class ObraSocial {
        public String codigo;
        public String nombre;
        public String tipo;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "resultadoCobertura", namespace = NS)
    public static class ResultadoCobertura {
        public boolean cubierto;
        public String plan;
        public int copagoPorcentaje;
        public String motivo;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "coberturaMedicamento", namespace = NS)
    public static class CoberturaMedicamento {
        public String medicamento;
        public boolean cubierto;
        public int descuentoPorcentaje;
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
