package ar.com.mediconecta.seguridad;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converter de JPA para los campos clinicos (SEC-10): cifra al guardar y descifra al leer.
 * El resto del codigo trabaja con el texto normal; en la base queda ilegible.
 *
 * Uso: {@code @Convert(converter = CampoClinicoCifrado.class)} en el campo de la entidad.
 */
@Converter
public class CampoClinicoCifrado implements AttributeConverter<String, String> {

    private final CifradoDatos cifrado;

    public CampoClinicoCifrado() {
        this(null);
    }

    CampoClinicoCifrado(CifradoDatos cifrado) {
        this.cifrado = cifrado;
    }

    private CifradoDatos cifrado() {
        return cifrado != null ? cifrado : CifradoDatos.configurado();
    }

    @Override
    public String convertToDatabaseColumn(String texto) {
        return texto == null ? null : cifrado().cifrar(texto);
    }

    @Override
    public String convertToEntityAttribute(String guardado) {
        return guardado == null ? null : cifrado().descifrar(guardado);
    }
}
