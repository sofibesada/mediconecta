package ar.com.mediconecta.telemedicina.integracion;

/** Respuesta de la plataforma de videollamadas al crear una sala (lo que nos interesa de ella). */
public class SalaExterna {
    public String id;
    /** Link para el paciente (invitado). */
    public String joinUrl;
    /** Link para el profesional (anfitrion / moderador). */
    public String hostUrl;
}
