package ar.com.videollamadas.mock;

public class Sala {
    public String id;
    public String referencia;
    /** Link para los invitados (el paciente). */
    public String joinUrl;
    /**
     * Link para el anfitrion (el profesional). En un proveedor real (Zoom, Jitsi as a
     * Service) este link trae una credencial de moderador; el Jitsi publico que usa el
     * simulador no la admite, asi que apunta a la misma sala con una marca de rol.
     */
    public String hostUrl;
    public String inicio;
    public int duracionMinutos;
    public String creadaEn;
}
