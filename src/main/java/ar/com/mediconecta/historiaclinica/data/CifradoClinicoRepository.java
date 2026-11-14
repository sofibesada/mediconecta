package ar.com.mediconecta.historiaclinica.data;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso directo (SQL nativo) a las columnas clinicas, sin pasar por el converter: lo usa
 * solo la migracion que cifra los datos guardados antes de SEC-10.
 *
 * Cada sentencia es un literal fijo (no se arma SQL concatenando nombres): los valores
 * viajan siempre como parametros.
 */
@ApplicationScoped
public class CifradoClinicoRepository {

    /** Una columna clinica cifrada y sus sentencias. */
    public enum Columna {
        DIAGNOSTICO_DESCRIPCION(
                "ALTER TABLE diagnosticos ALTER COLUMN descripcion TYPE varchar(8000)",
                "SELECT id, descripcion FROM diagnosticos WHERE descripcion IS NOT NULL AND descripcion NOT LIKE ?1",
                "UPDATE diagnosticos SET descripcion = ?1 WHERE id = ?2"),
        RECETA_MEDICAMENTO(
                "ALTER TABLE recetas ALTER COLUMN medicamento TYPE varchar(8000)",
                "SELECT id, medicamento FROM recetas WHERE medicamento IS NOT NULL AND medicamento NOT LIKE ?1",
                "UPDATE recetas SET medicamento = ?1 WHERE id = ?2"),
        RECETA_INDICACIONES(
                "ALTER TABLE recetas ALTER COLUMN indicaciones TYPE varchar(8000)",
                "SELECT id, indicaciones FROM recetas WHERE indicaciones IS NOT NULL AND indicaciones NOT LIKE ?1",
                "UPDATE recetas SET indicaciones = ?1 WHERE id = ?2");

        private final String ampliar;
        private final String pendientes;
        private final String actualizar;

        Columna(String ampliar, String pendientes, String actualizar) {
            this.ampliar = ampliar;
            this.pendientes = pendientes;
            this.actualizar = actualizar;
        }
    }

    /** Un valor de una columna clinica, tal como esta en la base. */
    public record ValorGuardado(Columna columna, Long id, String valor) {}

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    /** Hibernate (hbm2ddl=update) no agranda columnas existentes: el texto cifrado es mas largo. */
    public void ampliarColumnas() {
        for (Columna c : Columna.values()) {
            em.createNativeQuery(c.ampliar).executeUpdate();
        }
    }

    /** Valores que todavia estan en texto plano (sin el prefijo del formato cifrado). */
    public List<ValorGuardado> sinCifrar(String prefijo) {
        List<ValorGuardado> lista = new ArrayList<>();
        for (Columna c : Columna.values()) {
            @SuppressWarnings("unchecked")
            List<Object[]> filas = em.createNativeQuery(c.pendientes)
                    .setParameter(1, prefijo + "%")
                    .getResultList();
            for (Object[] f : filas) {
                lista.add(new ValorGuardado(c, ((Number) f[0]).longValue(), (String) f[1]));
            }
        }
        return lista;
    }

    public void actualizar(ValorGuardado v, String nuevoValor) {
        em.createNativeQuery(v.columna().actualizar)
                .setParameter(1, nuevoValor)
                .setParameter(2, v.id())
                .executeUpdate();
    }
}
