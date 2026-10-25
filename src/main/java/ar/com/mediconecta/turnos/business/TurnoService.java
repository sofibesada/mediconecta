package ar.com.mediconecta.turnos.business;

import ar.com.mediconecta.facturacion.business.IFacturacionService;
import ar.com.mediconecta.obrassociales.business.AuditoriaAutorizacionService;
import ar.com.mediconecta.obrassociales.business.AutorizacionRechazadaException;
import ar.com.mediconecta.obrassociales.business.IObraSocialService;
import ar.com.mediconecta.obrassociales.business.IObraSocialService.ResultadoAutorizacion;
import ar.com.mediconecta.obrassociales.integracion.ObrasSocialesNoDisponibleException;
import ar.com.mediconecta.obrassociales.model.IntentoAutorizacion;
import ar.com.mediconecta.turnos.data.TurnoRepository;
import ar.com.mediconecta.turnos.eventos.TipoEventoTurno;
import ar.com.mediconecta.turnos.eventos.TurnoEventoPublisher;
import ar.com.mediconecta.turnos.model.EstadoTurno;
import ar.com.mediconecta.turnos.model.Modalidad;
import ar.com.mediconecta.turnos.model.Turno;
import ar.com.mediconecta.seguridad.Roles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import ar.com.mediconecta.usuarios.model.Usuario;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.EJBException;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Reglas de negocio de los turnos. Es @Stateless: cada operacion es independiente y
 * no guarda nada de un cliente entre llamadas (el estado del turno vive en la base).
 * La conversacion de "agendar un turno" paso a paso la lleva AsistenteAgendamiento,
 * que si es @Stateful.
 *
 * Seguridad declarativa: @PermitAll en la clase (consultas) y @RolesAllowed en cada
 * operacion que cambia un turno. Ojo: WildFly niega por defecto los metodos sin anotar
 * de un EJB que tiene alguna anotacion de seguridad, por eso el @PermitAll de la clase.
 */
@Stateless
@PermitAll
public class TurnoService implements ITurnoService {

    private static final int MINUTOS_HOLD = 5;
    /** Valor de la consulta (system property mediconecta.consulta.valor). */
    private static final String VALOR_CONSULTA_POR_DEFECTO = "20000";
    private static final Logger LOG = Logger.getLogger(TurnoService.class.getName());

    @Inject
    private TurnoRepository turnoRepository;

    @Inject
    private IUsuarioService usuarioService;

    @Inject
    private TurnoEventoPublisher eventoPublisher;

    @Inject
    private IObraSocialService obraSocialService;

    @Inject
    private AuditoriaAutorizacionService auditoria;

    @Inject
    private IFacturacionService facturacionService;

    @PostConstruct
    public void init() {
        LOG.info("[Ciclo de vida] TurnoService @Stateless CREADO por WildFly (entra al pool) - instancia #" + System.identityHashCode(this));
    }

    @PreDestroy
    public void destroy() {
        LOG.info("[Ciclo de vida] TurnoService @Stateless DESTRUIDO por WildFly (sale del pool) - instancia #" + System.identityHashCode(this));
    }

    @Override
    public Turno buscarTurno(Long turnoId) {
        return turnoRepository.buscarPorId(turnoId);
    }

    @Override
    @RolesAllowed(Roles.PACIENTE)
    public void liberarReservaTemporal(Long pacienteId, Long turnoId) {
        Turno turno = turnoRepository.buscarPorId(turnoId);
        // Solo libera su propia reserva temporal: si ya se confirmo, vencio o es de otro, no hace nada.
        if (turno == null || turno.getEstado() != EstadoTurno.RESERVADO_TEMPORAL
                || !pacienteId.equals(turno.getPacienteId())) {
            return;
        }
        turno.setEstado(EstadoTurno.DISPONIBLE);
        turno.setPacienteId(null);
        turno.setHoldExpiraEn(null);
        turnoRepository.actualizar(turno);
    }

    @Override
    public List<Turno> consultarDisponibilidad(Long profesionalId) {
        return turnoRepository.listarDisponiblesPorProfesional(profesionalId);
    }

    @Override
    public List<Turno> listarTurnosDePaciente(Long pacienteId) {
        return turnoRepository.listarPorPaciente(pacienteId);
    }

    @Override
    public List<Turno> listarTurnosDeProfesional(Long profesionalId) {
        return turnoRepository.listarPorProfesional(profesionalId);
    }

    @Override
    @RolesAllowed(Roles.PACIENTE)
    public Turno reservarTemporalmente(Long pacienteId, Long turnoId) {
        if (!usuarioService.existeUsuario(pacienteId)) {
            throw new DatosTurnoInvalidosException("El paciente indicado no existe o está desactivado");
        }
        Turno turno = turnoRepository.buscarPorId(turnoId);
        // Una reserva temporal vencida (el paciente la abandono) cuenta como disponible.
        boolean holdVencido = turno != null && turno.getEstado() == EstadoTurno.RESERVADO_TEMPORAL
                && turno.getHoldExpiraEn() != null && turno.getHoldExpiraEn().isBefore(LocalDateTime.now());
        if (turno == null || (turno.getEstado() != EstadoTurno.DISPONIBLE && !holdVencido)) {
            throw new ConflictoTurnoException("El turno no está disponible");
        }
        if (turno.yaPaso()) {
            throw new ConflictoTurnoException("Ese horario ya pasó");
        }
        turno.setPacienteId(pacienteId);
        turno.setEstado(EstadoTurno.RESERVADO_TEMPORAL);
        turno.setHoldExpiraEn(LocalDateTime.now().plusMinutes(MINUTOS_HOLD));
        return turnoRepository.actualizar(turno);
    }

    @Override
    @RolesAllowed(Roles.PACIENTE)
    public Turno confirmarTurno(Long turnoId) {
        return confirmarTurno(turnoId, false);
    }

    /**
     * FLUJO CRITICO con transaccion declarativa (REQUIRED, la que abre el contenedor al
     * entrar al metodo). Todos los pasos se confirman juntos o no se confirma ninguno:
     *
     *   1. el turno pasa de RESERVADO_TEMPORAL a CONFIRMADO
     *   2. si el paciente tiene obra social, se pide la autorizacion por SOAP al legado
     *      (el intento se audita en OTRA transaccion, REQUIRES_NEW)
     *   3. se guardan la autorizacion y los importes (copago / a cargo de la obra social)
     *   4. se publica TURNO_CONFIRMADO en el topico (avisos, telemedicina)
     *   5. se crea el reclamo PENDIENTE y se envia a la cola de facturacion
     *
     * Si la obra social rechaza o no responde, AutorizacionRechazadaException
     * (@ApplicationException(rollback = true)) revierte todo: el turno sigue reservado y
     * no sale ningun mensaje. Una falla tecnica en los pasos 3-5 tambien revierte todo,
     * porque la base y los envios JMS participan de la misma transaccion XA.
     *
     * particular = true: el paciente elige no usar la obra social y paga el total.
     */
    @Override
    @RolesAllowed(Roles.PACIENTE)
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Turno confirmarTurno(Long turnoId, boolean particular) {
        Turno turno = turnoRepository.buscarPorId(turnoId);
        if (turno == null || turno.getEstado() != EstadoTurno.RESERVADO_TEMPORAL) {
            throw new ConflictoTurnoException("El turno no tiene una reserva temporal activa");
        }
        if (turno.getPacienteId() != null && !usuarioService.existeUsuario(turno.getPacienteId())) {
            throw new ConflictoTurnoException("El paciente de este turno ya no está activo");
        }
        if (turno.getHoldExpiraEn().isBefore(LocalDateTime.now())) {
            // ConflictoTurnoException es rollback = false: la liberacion del horario SI se guarda.
            turno.setEstado(EstadoTurno.DISPONIBLE);
            turno.setPacienteId(null);
            turnoRepository.actualizar(turno);
            throw new ConflictoTurnoException("El hold del turno expiró, volvé a reservarlo");
        }

        // Paso 1
        turno.setEstado(EstadoTurno.CONFIRMADO);
        turnoRepository.actualizar(turno);

        Usuario paciente = usuarioService.buscarParaSesion(turno.getPacienteId());
        BigDecimal valor = valorConsulta();
        boolean conObraSocial = !particular && paciente.tieneObraSocial();
        if (conObraSocial) {
            // Paso 2
            ResultadoAutorizacion autorizacion = autorizar(turno, paciente);
            // Paso 3
            BigDecimal copago = valor.multiply(BigDecimal.valueOf(paciente.getCopagoPorcentaje()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            turno.setObraSocial(paciente.getObraSocial());
            turno.setCodigoAutorizacion(autorizacion.codigo());
            turno.setImporteCopago(copago);
            turno.setImporteObraSocial(valor.subtract(copago));
        } else {
            turno.setObraSocial(null);
            turno.setCodigoAutorizacion(null);
            turno.setImporteCopago(valor);
            turno.setImporteObraSocial(null);
        }
        Turno confirmado = turnoRepository.actualizar(turno);

        // Paso 4
        eventoPublisher.publicar(TipoEventoTurno.TURNO_CONFIRMADO, confirmado, null);
        // Paso 5
        if (conObraSocial) {
            facturacionService.registrarPrestacion(confirmado.getId(), paciente.getId(), paciente.getObraSocial(),
                    paciente.getNumeroAfiliado(), confirmado.getCodigoAutorizacion(), confirmado.getImporteObraSocial());
        }
        LOG.info("[Transaccion] turno #" + confirmado.getId() + " confirmado "
                + (conObraSocial ? "con " + confirmado.getObraSocial() + " (" + confirmado.getCodigoAutorizacion() + ")" : "como particular"));
        return confirmado;
    }

    private ResultadoAutorizacion autorizar(Turno turno, Usuario paciente) {
        ResultadoAutorizacion r;
        try {
            r = obraSocialService.autorizarConsulta(paciente.getObraSocial(), paciente.getNumeroAfiliado(),
                    turno.getId(), turno.getFechaHora());
        } catch (EJBException e) {
            if (!causadaPor(e, ObrasSocialesNoDisponibleException.class)) {
                throw e;
            }
            auditoria.registrar(turno.getId(), paciente.getId(), paciente.getObraSocial(),
                    IntentoAutorizacion.Resultado.SIN_RESPUESTA, null, "El sistema de la obra social no respondió");
            throw new AutorizacionRechazadaException(
                    "El sistema de " + paciente.getObraSocial() + " no responde. Probá de nuevo en unos minutos o confirmá como particular.");
        }
        auditoria.registrar(turno.getId(), paciente.getId(), paciente.getObraSocial(),
                r.autorizada() ? IntentoAutorizacion.Resultado.AUTORIZADA : IntentoAutorizacion.Resultado.RECHAZADA,
                r.codigo(), r.motivo());
        if (!r.autorizada()) {
            throw new AutorizacionRechazadaException(
                    "La obra social no autorizó la consulta: " + r.motivo() + ". Podés confirmarla como particular.");
        }
        return r;
    }

    private static boolean causadaPor(Throwable t, Class<? extends Throwable> tipo) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (tipo.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal valorConsulta() {
        try {
            return new BigDecimal(System.getProperty("mediconecta.consulta.valor", VALOR_CONSULTA_POR_DEFECTO));
        } catch (NumberFormatException e) {
            return new BigDecimal(VALOR_CONSULTA_POR_DEFECTO);
        }
    }

    @Override
    @RolesAllowed({Roles.PACIENTE, Roles.PROFESIONAL, Roles.ADMIN})
    public void cancelarTurno(Long turnoId) {
        Turno turno = turnoRepository.buscarPorId(turnoId);
        if (turno == null || turno.getEstado() == EstadoTurno.CANCELADO) {
            return;
        }
        if (turno.isFinalizado()) {
            throw new ConflictoTurnoException("No se puede cancelar: el turno ya finalizó");
        }
        PoliticaCancelacion politica = turno.isUrgente()
                ? new PoliticaCancelacionUrgencia()
                : new PoliticaCancelacionEstandar();

        if (!politica.puedeCancelarse(turno)) {
            throw new ConflictoTurnoException("No se puede cancelar: faltan menos de 24hs para el turno");
        }
        turno.setEstado(EstadoTurno.CANCELADO);
        turnoRepository.actualizar(turno);
        if (turno.getPacienteId() != null) {
            eventoPublisher.publicar(TipoEventoTurno.TURNO_CANCELADO, turno, null);
        }
    }

    @Override
    @RolesAllowed(Roles.PROFESIONAL)
    public Turno reprogramarTurno(Long profesionalId, Long turnoId, LocalDateTime nuevaFecha) {
        Turno turno = turnoRepository.buscarPorId(turnoId);
        if (turno == null) {
            throw new ConflictoTurnoException("Turno no encontrado");
        }
        if (!turno.getProfesionalId().equals(profesionalId)) {
            throw new DatosTurnoInvalidosException("Solo el profesional del turno puede reprogramarlo");
        }
        if (turno.getEstado() == EstadoTurno.CANCELADO) {
            throw new ConflictoTurnoException("No se puede reprogramar un turno cancelado");
        }
        if (turno.isFinalizado()) {
            throw new ConflictoTurnoException("No se puede reprogramar un turno que ya finalizó");
        }
        if (turno.getEstado() == EstadoTurno.RESERVADO_TEMPORAL) {
            throw new ConflictoTurnoException("El paciente está confirmando este turno; esperá a que lo confirme o se libere");
        }
        if (nuevaFecha == null || !nuevaFecha.isAfter(LocalDateTime.now())) {
            throw new DatosTurnoInvalidosException("La nueva fecha tiene que ser futura");
        }
        if (nuevaFecha.equals(turno.getFechaHora())) {
            throw new DatosTurnoInvalidosException("La nueva fecha es igual a la actual");
        }
        if (turnoRepository.existeTurnoEnHorario(profesionalId, nuevaFecha, turnoId)) {
            throw new ConflictoTurnoException("Ya tenés otro turno en ese horario");
        }
        LocalDateTime fechaAnterior = turno.getFechaHora();
        turno.setFechaHora(nuevaFecha);
        Turno reprogramado = turnoRepository.actualizar(turno);
        if (reprogramado.getPacienteId() != null) {
            eventoPublisher.publicar(TipoEventoTurno.TURNO_REPROGRAMADO, reprogramado, fechaAnterior);
        }
        return reprogramado;
    }

    @Override
    @RolesAllowed(Roles.PROFESIONAL)
    public Turno crearTurnoDisponible(Long profesionalId, LocalDateTime fechaHora, Modalidad modalidad) {
        if (!usuarioService.esProfesional(profesionalId)) {
            throw new DatosTurnoInvalidosException("El usuario indicado no existe o no es un profesional");
        }
        if (fechaHora == null || !fechaHora.isAfter(LocalDateTime.now())) {
            throw new DatosTurnoInvalidosException("La fecha del turno tiene que ser futura");
        }
        if (turnoRepository.existeTurnoEnHorario(profesionalId, fechaHora, 0L)) {
            throw new ConflictoTurnoException("Ya tenés un turno publicado en ese horario");
        }
        Turno turno = new Turno();
        turno.setProfesionalId(profesionalId);
        turno.setFechaHora(fechaHora);
        turno.setEstado(EstadoTurno.DISPONIBLE);
        turno.setModalidad(modalidad != null ? modalidad : Modalidad.PRESENCIAL);
        return turnoRepository.guardar(turno);
    }
}
