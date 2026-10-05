package ar.com.mediconecta.telemedicina.presentation;

import ar.com.mediconecta.telemedicina.model.SalaVirtual;
import java.time.LocalDateTime;

/**
 * Cada participante ve SOLO su link: al profesional le llega el de anfitrion y al
 * paciente el de invitado. No se expone el id interno del proveedor.
 */
public class SalaResponse {
    public Long turnoId;
    public String url;
    /** ANFITRION (profesional) o PARTICIPANTE (paciente). */
    public String rol;
    public LocalDateTime creadaEn;

    public static SalaResponse from(SalaVirtual s, Long usuarioId) {
        SalaResponse r = new SalaResponse();
        r.turnoId = s.getTurnoId();
        r.url = s.urlPara(usuarioId);
        r.rol = s.esAnfitrion(usuarioId) ? "ANFITRION" : "PARTICIPANTE";
        r.creadaEn = s.getCreadaEn();
        return r;
    }
}
