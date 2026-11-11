package ar.com.mediconecta.usuarios.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.usuarios.data.UsuarioRepository;
import ar.com.mediconecta.usuarios.model.RolUsuario;
import ar.com.mediconecta.usuarios.model.Usuario;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final Long ADMIN = 1L;

    @Mock
    private UsuarioRepository repo;

    @InjectMocks
    private UsuarioService service;

    @BeforeEach
    void preparar() {
        lenient().when(repo.guardar(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(repo.actualizar(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(repo.buscarPorId(ADMIN)).thenReturn(usuario(ADMIN, RolUsuario.ADMIN, "x"));
    }

    @Nested
    class Registro {

        @Test
        void creaUnPacienteConLaContrasenaHasheada() {
            Usuario u = service.registrarUsuario(datos("Ana", "ana@test.local", "1234"));

            assertEquals(RolUsuario.PACIENTE, u.getRol());
            assertFalse(u.isDebeCambiarPassword());
            assertTrue(BCrypt.checkpw("1234", u.getPassword()));
        }

        @Test
        void aunqueMandeOtroRolQuedaPaciente() {
            Usuario d = datos("Ana", "ana@test.local", "1234");
            d.setRol(RolUsuario.ADMIN);
            assertEquals(RolUsuario.PACIENTE, service.registrarUsuario(d).getRol());
        }

        @Test
        void camposObligatorios() {
            assertThrows(IllegalArgumentException.class, () -> service.registrarUsuario(datos(null, "a@b", "1")));
            assertThrows(IllegalArgumentException.class, () -> service.registrarUsuario(datos(" ", "a@b", "1")));
            assertThrows(IllegalArgumentException.class, () -> service.registrarUsuario(datos("A", null, "1")));
            assertThrows(IllegalArgumentException.class, () -> service.registrarUsuario(datos("A", "", "1")));
            assertThrows(IllegalArgumentException.class, () -> service.registrarUsuario(datos("A", "a@b", null)));
            assertThrows(IllegalArgumentException.class, () -> service.registrarUsuario(datos("A", "a@b", " ")));
        }

        @Test
        void emailRepetido() {
            when(repo.buscarPorEmail("ana@test.local")).thenReturn(new Usuario());
            assertThrows(IllegalStateException.class, () -> service.registrarUsuario(datos("Ana", "ana@test.local", "1234")));
        }
    }

    @Nested
    class Profesionales {

        @Test
        void elAdminLoCreaConContrasenaTemporal() {
            Usuario p = service.crearProfesional(ADMIN, datos("Dr", "dr@test.local", "temp"));

            assertEquals(RolUsuario.PROFESIONAL, p.getRol());
            assertTrue(p.isDebeCambiarPassword());
            assertTrue(BCrypt.checkpw("temp", p.getPassword()));
        }

        @Test
        void soloUnAdmin() {
            when(repo.buscarPorId(5L)).thenReturn(usuario(5L, RolUsuario.PACIENTE, "x"));
            assertThrows(IllegalArgumentException.class, () -> service.crearProfesional(5L, datos("Dr", "dr@x", "t")));
            assertThrows(IllegalArgumentException.class, () -> service.crearProfesional(null, datos("Dr", "dr@x", "t")));
        }

        @Test
        void validaciones() {
            assertThrows(IllegalArgumentException.class, () -> service.crearProfesional(ADMIN, datos(null, "dr@x", "t")));
            assertThrows(IllegalArgumentException.class, () -> service.crearProfesional(ADMIN, datos("Dr", " ", "t")));
            assertThrows(IllegalArgumentException.class, () -> service.crearProfesional(ADMIN, datos("Dr", "dr@x", "")));
            assertThrows(IllegalArgumentException.class, () -> service.crearProfesional(ADMIN, datos("", "dr@x", "t")));
            assertThrows(IllegalArgumentException.class, () -> service.crearProfesional(ADMIN, datos("Dr", null, "t")));
            assertThrows(IllegalArgumentException.class, () -> service.crearProfesional(ADMIN, datos("Dr", "dr@x", null)));
            when(repo.buscarPorEmail("dr@x")).thenReturn(new Usuario());
            assertThrows(IllegalStateException.class, () -> service.crearProfesional(ADMIN, datos("Dr", "dr@x", "t")));
        }

        @Test
        void listarSoloLosActivos() {
            Usuario activo = usuario(2L, RolUsuario.PROFESIONAL, "x");
            Usuario inactivo = usuario(3L, RolUsuario.PROFESIONAL, "x");
            inactivo.setActivo(false);
            when(repo.listarPorRol(RolUsuario.PROFESIONAL)).thenReturn(List.of(activo, inactivo));
            when(repo.listarTodos()).thenReturn(List.of(activo, inactivo));

            assertEquals(List.of(activo), service.listarProfesionales());
            assertEquals(2, service.listarTodos().size());
        }
    }

    @Nested
    class Contrasenas {

        private Usuario u;

        @BeforeEach
        void preparar() {
            u = usuario(5L, RolUsuario.PROFESIONAL, "actual");
            u.setDebeCambiarPassword(true);
            lenient().when(repo.buscarPorId(5L)).thenReturn(u);
        }

        @Test
        void cambiarLaPropia() {
            Usuario r = service.cambiarPassword(5L, "actual", "nueva1");
            assertTrue(BCrypt.checkpw("nueva1", r.getPassword()));
            assertFalse(r.isDebeCambiarPassword());
        }

        @Test
        void reglasDelCambio() {
            assertThrows(IllegalArgumentException.class, () -> service.cambiarPassword(5L, "otra", "nueva1"));
            assertThrows(IllegalArgumentException.class, () -> service.cambiarPassword(5L, null, "nueva1"));
            assertThrows(IllegalArgumentException.class, () -> service.cambiarPassword(5L, "actual", "abc"));
            assertThrows(IllegalArgumentException.class, () -> service.cambiarPassword(5L, "actual", null));
            assertThrows(IllegalArgumentException.class, () -> service.cambiarPassword(5L, "actual", "actual"));
            assertThrows(IllegalArgumentException.class, () -> service.cambiarPassword(99L, "actual", "nueva1"));
            u.setActivo(false);
            assertThrows(IllegalStateException.class, () -> service.cambiarPassword(5L, "actual", "nueva1"));
        }

        @Test
        void elAdminReseteaUnaTemporalDe8Caracteres() {
            String temporal = service.resetearPassword(ADMIN, 5L);

            assertEquals(8, temporal.length());
            assertTrue(BCrypt.checkpw(temporal, u.getPassword()));
            assertTrue(u.isDebeCambiarPassword());
        }

        @Test
        void resetearReglas() {
            assertThrows(IllegalArgumentException.class, () -> service.resetearPassword(ADMIN, 99L));
            assertThrows(IllegalStateException.class, () -> service.resetearPassword(ADMIN, ADMIN));
            assertThrows(IllegalArgumentException.class, () -> service.resetearPassword(5L, ADMIN));
        }

        @Test
        void autenticar() {
            u.setEmail("dr@test.local");
            when(repo.buscarPorEmail("dr@test.local")).thenReturn(u);
            assertSame(u, service.autenticar("dr@test.local", "actual"));
            assertNull(service.autenticar("dr@test.local", "mal"));
            assertNull(service.autenticar("nadie@test.local", "actual"));
        }
    }

    @Nested
    class Cuentas {

        @Test
        void activarYDesactivar() {
            Usuario p = usuario(5L, RolUsuario.PACIENTE, "x");
            when(repo.buscarPorId(5L)).thenReturn(p);

            assertFalse(service.cambiarEstadoUsuario(ADMIN, 5L, false).isActivo());
            assertTrue(service.cambiarEstadoUsuario(ADMIN, 5L, true).isActivo());
            assertThrows(IllegalArgumentException.class, () -> service.cambiarEstadoUsuario(ADMIN, 99L, false));
            assertThrows(IllegalStateException.class, () -> service.cambiarEstadoUsuario(ADMIN, ADMIN, false));
        }

        @Test
        void sesionYExistenciaSoloParaActivos() {
            Usuario activo = usuario(5L, RolUsuario.PROFESIONAL, "x");
            Usuario inactivo = usuario(6L, RolUsuario.PROFESIONAL, "x");
            inactivo.setActivo(false);
            when(repo.buscarPorId(5L)).thenReturn(activo);
            when(repo.buscarPorId(6L)).thenReturn(inactivo);

            assertSame(activo, service.buscarParaSesion(5L));
            assertNull(service.buscarParaSesion(6L));
            assertNull(service.buscarParaSesion(null));
            assertTrue(service.existeUsuario(5L));
            assertFalse(service.existeUsuario(6L));
            assertFalse(service.existeUsuario(null));
            assertTrue(service.esProfesional(5L));
            assertFalse(service.esProfesional(6L));
            assertFalse(service.esProfesional(ADMIN));
            assertFalse(service.esProfesional(77L));
            assertTrue(service.esAdmin(ADMIN));
            assertFalse(service.esAdmin(5L));
            assertFalse(service.esAdmin(null));
            assertSame(activo, service.buscarUsuario(5L));
        }

        @Test
        void sembrarAdminSoloSiNoExiste() {
            service.sembrarAdmin("Admin", "admin@test.local", "clave");
            verify(repo).guardar(any());

            when(repo.buscarPorEmail("ya@test.local")).thenReturn(new Usuario());
            service.sembrarAdmin("Admin", "ya@test.local", "clave");
            verify(repo).guardar(any());
        }

        @Test
        void nombres() {
            Usuario p = usuario(5L, RolUsuario.PACIENTE, "x");
            p.setNombre("Ana");
            when(repo.buscarPorId(5L)).thenReturn(p);
            when(repo.listarPorIds(Set.of(5L))).thenReturn(List.of(p));

            assertEquals("Ana", service.nombreDe(5L));
            assertNull(service.nombreDe(null));
            assertNull(service.nombreDe(77L));
            Set<Long> ids = new HashSet<>();
            ids.add(5L);
            ids.add(null);
            assertEquals(Map.of(5L, "Ana"), service.nombresDe(ids));
            Set<Long> soloNull = new HashSet<>();
            soloNull.add(null);
            assertEquals(Map.of(), service.nombresDe(soloNull));
        }

        @Test
        void cobertura() {
            Usuario p = usuario(5L, RolUsuario.PACIENTE, "x");
            when(repo.buscarPorId(5L)).thenReturn(p);

            Usuario conOs = service.guardarCobertura(5L, "OSMED", "12345678", "Plan 210", 30);
            assertTrue(conOs.tieneObraSocial());
            assertEquals(30, conOs.getCopagoPorcentaje());
            assertEquals("Plan 210", conOs.getPlanObraSocial());

            Usuario sinOs = service.quitarCobertura(5L);
            assertFalse(sinOs.tieneObraSocial());
            assertNull(sinOs.getNumeroAfiliado());
        }

        @Test
        void cicloDeVida() {
            service.init();
            service.destroy();
            verify(repo, never()).guardar(any());
        }
    }

    private static Usuario datos(String nombre, String email, String password) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setEmail(email);
        u.setPassword(password);
        return u;
    }

    private static Usuario usuario(Long id, RolUsuario rol, String password) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setRol(rol);
        u.setPassword(BCrypt.hashpw(password, BCrypt.gensalt(4)));
        return u;
    }
}
