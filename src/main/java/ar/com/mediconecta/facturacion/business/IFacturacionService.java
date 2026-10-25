package ar.com.mediconecta.facturacion.business;

import ar.com.mediconecta.facturacion.model.Reclamo;
import java.math.BigDecimal;
import java.util.List;

public interface IFacturacionService {

    /** Crea el reclamo PENDIENTE y lo encola. Se llama dentro de la transaccion de la confirmacion. */
    void registrarPrestacion(Long turnoId, Long pacienteId, String obraSocial, String numeroAfiliado,
                             String codigoAutorizacion, BigDecimal importe);

    /** Lo llama el consumidor de la cola: reclama la prestacion al sistema legado. */
    void procesarReclamo(Long reclamoId);

    List<Reclamo> listarRecientes();
}
