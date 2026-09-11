package ar.com.mediconecta.usuarios.data;

import ar.com.mediconecta.usuarios.model.RolUsuario;
import ar.com.mediconecta.usuarios.model.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

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

    public Usuario buscarPorEmail(String email) {
        return em.createQuery("SELECT u FROM Usuario u WHERE u.email = :email", Usuario.class)
                .setParameter("email", email)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    public List<Usuario> listarPorRol(RolUsuario rol) {
        return em.createQuery("SELECT u FROM Usuario u WHERE u.rol = :rol ORDER BY u.nombre", Usuario.class)
                .setParameter("rol", rol)
                .getResultList();
    }

    public List<Usuario> listarTodos() {
        return em.createQuery("SELECT u FROM Usuario u ORDER BY u.rol, u.nombre", Usuario.class)
                .getResultList();
    }

    public List<Usuario> listarPorIds(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return em.createQuery("SELECT u FROM Usuario u WHERE u.id IN :ids", Usuario.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    public Usuario actualizar(Usuario usuario) {
        return em.merge(usuario);
    }
}