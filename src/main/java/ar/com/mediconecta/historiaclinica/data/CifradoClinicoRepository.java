package ar.com.mediconecta.historiaclinica.data;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

/**
 * Acceso directo (SQL nativo) a las columnas clinicas, sin pasar por el converter: lo usa
 * solo la migracion que cifra los datos guardados antes de SEC-10.
 */
@ApplicationScoped
public class CifradoClinicoRepository {

    /** Fila de una columna clinica: tabla, columna, id y el valor tal como esta en la base. */
    public record ValorGuardado(String tabla, String columna, Long id, String valor) {}

    /** Columnas clinicas cifradas: tabla y columna. */
    public static final List<String[]> COLUMNAS = List.of(
            new String[] {"diagnosticos", "descripcion"},
            new String[] {"recetas", "medicamento"},
            new String[] {"recetas", "indicaciones"});

    static final int LARGO_COLUMNA = 8000;

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    /** Hibernate (hbm2ddl=update) no agranda columnas existentes: el texto cifrado es mas largo. */
    public void ampliarColumnas() {
        for (String[] c : COLUMNAS) {
            em.createNativeQuery("ALTER TABLE " + c[0] + " ALTER COLUMN " + c[1] + " TYPE varchar(" + LARGO_COLUMNA + ")")
                    .executeUpdate();
        }
    }

    /** Valores que todavia estan en texto plano (sin el prefijo del formato cifrado). */
    public List<ValorGuardado> sinCifrar(String prefijo) {
        java.util.ArrayList<ValorGuardado> lista = new java.util.ArrayList<>();
        for (String[] c : COLUMNAS) {
            @SuppressWarnings("unchecked")
            List<Object[]> filas = em.createNativeQuery("SELECT id, " + c[1] + " FROM " + c[0]
                            + " WHERE " + c[1] + " IS NOT NULL AND " + c[1] + " NOT LIKE ?1")
                    .setParameter(1, prefijo + "%")
                    .getResultList();
            for (Object[] f : filas) {
                lista.add(new ValorGuardado(c[0], c[1], ((Number) f[0]).longValue(), (String) f[1]));
            }
        }
        return lista;
    }

    public void actualizar(ValorGuardado v, String nuevoValor) {
        em.createNativeQuery("UPDATE " + v.tabla() + " SET " + v.columna() + " = ?1 WHERE id = ?2")
                .setParameter(1, nuevoValor)
                .setParameter(2, v.id())
                .executeUpdate();
    }
}
