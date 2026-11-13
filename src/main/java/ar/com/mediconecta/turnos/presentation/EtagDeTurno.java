package ar.com.mediconecta.turnos.presentation;

import ar.com.mediconecta.config.ApiError;
import ar.com.mediconecta.turnos.model.Turno;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Response;

/**
 * ETag de un turno: su numero de version (@Version de JPA), que cambia con cada
 * modificacion. Sirve para:
 *
 * - GET con If-None-Match: si el turno no cambio, 304 sin cuerpo.
 * - PUT/DELETE con If-Match: si el turno cambio desde que el cliente lo leyo, 412 en lugar
 *   de pisar el cambio de otro (control de concurrencia optimista).
 *
 * If-Match es opcional: sin el encabezado, la operacion se hace como antes.
 */
public final class EtagDeTurno {

    static final String CAMBIO = "El turno cambió desde que lo consultaste. Actualizá la pantalla y probá de nuevo.";

    private EtagDeTurno() {}

    public static EntityTag de(Turno turno) {
        return new EntityTag(String.valueOf(turno.getVersion() == null ? 0 : turno.getVersion()));
    }

    /** Lanza 412 si vino If-Match y no coincide con la version actual del turno. */
    public static void exigirIfMatch(String ifMatch, Turno turno) {
        if (ifMatch == null || ifMatch.isBlank() || turno == null) {
            return;
        }
        String actual = de(turno).toString();
        for (String valor : ifMatch.split(",")) {
            String v = valor.trim();
            if (v.equals("*") || v.equals(actual) || v.equals("W/" + actual)) {
                return;
            }
        }
        throw new WebApplicationException(ApiError.respuesta(Response.Status.PRECONDITION_FAILED, CAMBIO));
    }

    /** true si el cliente ya tiene esta version (If-None-Match coincide). */
    public static boolean noCambio(String ifNoneMatch, Turno turno) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String actual = de(turno).toString();
        for (String valor : ifNoneMatch.split(",")) {
            String v = valor.trim();
            if (v.equals("*") || v.equals(actual) || v.equals("W/" + actual)) {
                return true;
            }
        }
        return false;
    }
}
