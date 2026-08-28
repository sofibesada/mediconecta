package ar.com.mediconecta.usuarios.data;

import ar.com.mediconecta.usuarios.model.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@ApplicationScoped
public class UsuarioRepository {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public Usuario guardar(Usuario usuario) {
        em.persist(usuario);
        return usuario;
    }

    public Usuario buscarPorId(Long id) {
        return em.find(Usuario.class, id);
    }
}