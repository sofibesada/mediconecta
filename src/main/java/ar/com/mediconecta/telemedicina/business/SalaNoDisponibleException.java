package ar.com.mediconecta.telemedicina.business;

import jakarta.ejb.ApplicationException;

/**
 * El turno no tiene sala (todavia no se creo, es presencial o fue cancelado) o
 * quien la pide no es el paciente ni el profesional. Excepcion de aplicacion: es un
 * resultado esperado, no un error del sistema (el mapper la traduce a 400).
 */
@ApplicationException(rollback = false)
public class SalaNoDisponibleException extends IllegalArgumentException {
    public SalaNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
