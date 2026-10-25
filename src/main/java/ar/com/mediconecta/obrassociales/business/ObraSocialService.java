package ar.com.mediconecta.obrassociales.business;

import ar.com.mediconecta.obrassociales.integracion.ContratoLegado;
import ar.com.mediconecta.obrassociales.integracion.ObrasSocialesClient;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Adapter del sistema legado de obras sociales: llama al servicio SOAP y traduce sus
 * tipos (ContratoLegado) a los del dominio. Si el legado no responde, propaga
 * ObrasSocialesNoDisponibleException.
 *
 * La "referencia" que se manda en cada pedido es el id del turno: si el mismo pedido se
 * repite (reintento, reentrega JMS), el legado devuelve el mismo resultado.
 */
@Stateless
public class ObraSocialService implements IObraSocialService {

    @Inject
    private ObrasSocialesClient cliente;

    @Override
    public List<ObraSocialDisponible> listarObrasSociales() {
        return cliente.llamar("listarObrasSociales", p -> p.listarObrasSociales()).stream()
                .map(o -> new ObraSocialDisponible(o.codigo, o.nombre, o.tipo))
                .toList();
    }

    @Override
    public CoberturaVerificada verificarCobertura(String obraSocial, String numeroAfiliado) {
        ContratoLegado.ResultadoCobertura r = cliente.llamar("consultarCobertura",
                p -> p.consultarCobertura(obraSocial, numeroAfiliado));
        return new CoberturaVerificada(r.cubierto, r.plan, r.copagoPorcentaje, r.motivo);
    }

    @Override
    public ResultadoAutorizacion autorizarConsulta(String obraSocial, String numeroAfiliado, Long turnoId, LocalDateTime fecha) {
        ContratoLegado.ResultadoAutorizacion r = cliente.llamar("autorizarPrestacion",
                p -> p.autorizarPrestacion(obraSocial, numeroAfiliado, PRESTACION_CONSULTA, fecha.toString(), "turno-" + turnoId));
        return new ResultadoAutorizacion(r.autorizada, r.codigoAutorizacion, r.motivo);
    }

    @Override
    public List<CoberturaMedicamento> coberturaMedicamentos(String obraSocial, String numeroAfiliado, List<String> medicamentos) {
        if (medicamentos.isEmpty()) {
            return List.of();
        }
        return cliente.llamar("consultarCoberturaMedicamentos",
                        p -> p.consultarCoberturaMedicamentos(obraSocial, numeroAfiliado, medicamentos)).stream()
                .map(c -> new CoberturaMedicamento(c.medicamento, c.cubierto, c.descuentoPorcentaje))
                .toList();
    }

    @Override
    public ResultadoReclamo registrarReclamo(String obraSocial, String numeroAfiliado, String codigoAutorizacion,
                                             BigDecimal importe, Long turnoId) {
        ContratoLegado.ResultadoReclamo r = cliente.llamar("registrarReclamo",
                p -> p.registrarReclamo(obraSocial, numeroAfiliado, codigoAutorizacion, importe, "reclamo-turno-" + turnoId));
        return new ResultadoReclamo(r.aceptado, r.numeroReclamo, r.importeReconocido, r.motivo);
    }
}
