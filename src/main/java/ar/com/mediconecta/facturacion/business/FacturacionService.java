package ar.com.mediconecta.facturacion.business;

import ar.com.mediconecta.facturacion.data.ReclamoRepository;
import ar.com.mediconecta.facturacion.messaging.FacturacionColaPublisher;
import ar.com.mediconecta.facturacion.model.Reclamo;
import ar.com.mediconecta.obrassociales.business.IObraSocialService;
import ar.com.mediconecta.obrassociales.business.IObraSocialService.ResultadoReclamo;
import ar.com.mediconecta.seguridad.Roles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Facturacion de las consultas a las obras sociales.
 *
 * Es asincronica por COLA punto a punto: cada reclamo lo tiene que procesar un unico
 * consumidor, una sola vez, y el legado puede estar lento o caido sin que eso afecte a
 * la confirmacion del turno. Si el legado no responde, la excepcion de sistema hace
 * rollback de la transaccion del consumidor y Artemis reintenta (3 veces, despues DLQ).
 */
@Stateless
@PermitAll
public class FacturacionService implements IFacturacionService {

    private static final Logger LOG = Logger.getLogger(FacturacionService.class.getName());

    @Inject
    private ReclamoRepository reclamoRepository;

    @Inject
    private FacturacionColaPublisher colaPublisher;

    @Inject
    private IObraSocialService obraSocialService;

    @Override
    public void registrarPrestacion(Long turnoId, Long pacienteId, String obraSocial, String numeroAfiliado,
                                    String codigoAutorizacion, BigDecimal importe) {
        Reclamo r = new Reclamo();
        r.setTurnoId(turnoId);
        r.setPacienteId(pacienteId);
        r.setObraSocial(obraSocial);
        r.setNumeroAfiliado(numeroAfiliado);
        r.setCodigoAutorizacion(codigoAutorizacion);
        r.setImporte(importe);
        r.setEstado(Reclamo.Estado.PENDIENTE);
        r.setCreadoEn(LocalDateTime.now());
        reclamoRepository.guardar(r);
        colaPublisher.enviar(r.getId(), turnoId);
    }

    @Override
    public void procesarReclamo(Long reclamoId) {
        Reclamo r = reclamoRepository.buscarPorId(reclamoId);
        if (r == null || r.getEstado() != Reclamo.Estado.PENDIENTE) {
            // Mensaje repetido (entrega "al menos una vez"): ya se proceso.
            LOG.info("[Facturacion] reclamo #" + reclamoId + " ya procesado, se ignora el mensaje repetido");
            return;
        }
        ResultadoReclamo resultado = obraSocialService.registrarReclamo(r.getObraSocial(), r.getNumeroAfiliado(),
                r.getCodigoAutorizacion(), r.getImporte(), r.getTurnoId());
        r.setEstado(resultado.aceptado() ? Reclamo.Estado.ENVIADO : Reclamo.Estado.RECHAZADO);
        r.setNumeroReclamo(resultado.numero());
        r.setMotivo(resultado.motivo());
        r.setProcesadoEn(LocalDateTime.now());
        LOG.info("[Facturacion] reclamo #" + reclamoId + " (turno #" + r.getTurnoId() + ") -> " + r.getEstado()
                + (resultado.aceptado() ? " " + resultado.numero() : ": " + resultado.motivo()));
    }

    @Override
    @RolesAllowed(Roles.ADMIN)
    public List<Reclamo> listarRecientes() {
        return reclamoRepository.listarRecientes(50);
    }
}
