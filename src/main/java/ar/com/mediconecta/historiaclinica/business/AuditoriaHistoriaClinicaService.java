package ar.com.mediconecta.historiaclinica.business;

import ar.com.mediconecta.historiaclinica.data.AccesoHistoriaClinicaRepository;
import ar.com.mediconecta.historiaclinica.model.AccesoHistoriaClinica;
import ar.com.mediconecta.historiaclinica.model.AccesoHistoriaClinica.Recurso;
import ar.com.mediconecta.historiaclinica.model.AccesoHistoriaClinica.Resultado;
import ar.com.mediconecta.seguridad.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Auditoria de las consultas a datos clinicos (SEC-09). Cada consulta a una historia
 * clinica o a una ficha deja un registro con quien, que recurso, de que paciente, cuando y
 * con que resultado. Solo se guardan identificadores.
 *
 * El registro va en su propia transaccion (REQUIRES_NEW): un acceso denegado queda
 * registrado aunque el pedido termine en error, y nunca figura como AUTORIZADO.
 */
@Stateless
public class AuditoriaHistoriaClinicaService {

    static final int MAXIMO_LISTADO = 200;
    private static final Set<String> ROLES = Set.of(Roles.PACIENTE, Roles.PROFESIONAL, Roles.ADMIN);

    @Inject
    private AccesoHistoriaClinicaRepository repository;

    @RolesAllowed({Roles.PACIENTE, Roles.PROFESIONAL, Roles.ADMIN})
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public AccesoHistoriaClinica registrar(Long usuarioId, String rol, Long pacienteId, Recurso recurso, Resultado resultado) {
        if (usuarioId == null || pacienteId == null || recurso == null || resultado == null || rol == null || !ROLES.contains(rol)) {
            throw new IllegalArgumentException("Datos de auditoría incompletos");
        }
        AccesoHistoriaClinica a = new AccesoHistoriaClinica();
        a.setUsuarioId(usuarioId);
        a.setRol(rol);
        a.setPacienteId(pacienteId);
        a.setRecurso(recurso);
        a.setResultado(resultado);
        a.setFechaHora(LocalDateTime.now());
        return repository.guardar(a);
    }

    /** Para el administrador: las consultas a la historia de un paciente, o las ultimas de todas. */
    @RolesAllowed(Roles.ADMIN)
    public List<AccesoHistoriaClinica> listar(Long pacienteId) {
        return pacienteId == null
                ? repository.listarRecientes(MAXIMO_LISTADO)
                : repository.listarDePaciente(pacienteId, MAXIMO_LISTADO);
    }
}
