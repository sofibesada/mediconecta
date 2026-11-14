package ar.com.mediconecta.usuarios.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.notificaciones.business.INotificacionService;
import ar.com.mediconecta.usuarios.data.UsuarioRepository;
import ar.com.mediconecta.usuarios.model.RolUsuario;
import ar.com.mediconecta.usuarios.model.Usuario;
import java.time.LocalDateTime;
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

/** Perfil propio, especialidades y pedido de recuperación de contraseña. */
class PerfilYRecuperacionTest {

    private static Usuario usuario(Long id, RolUsuario rol, String email) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setRol(rol);
        u.setNombre("Usuario " + id);
        u.setEmail(email);
        u.setPassword(BCrypt.hashpw("clave1234", BCrypt.gensalt(4)));
        return u;
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Perfil {

        @Mock private UsuarioRepository repo;
        @InjectMocks private UsuarioService service;
        private Usuario paciente;
        private Usuario profesional;

        @BeforeEach
        void preparar() {
            paciente = usuario(5L, RolUsuario.PACIENTE, "ana@test.local");
            profesional = usuario(6L, RolUsuario.PROFESIONAL, "dra@test.local");
            lenient().when(repo.buscarPorId(5L)).thenReturn(paciente);
            lenient().when(repo.buscarPorId(6L)).thenReturn(profesional);
            lenient().when(repo.actualizar(any())).thenAnswer(i -> i.getArgument(0));
        }

        @Test
        void cambiaElNombreSinPedirContrasena() {
            Usuario u = service.actualizarPerfil(5L, new DatosPerfil("  Ana Pérez  ", "ANA@test.local", null, null));
            assertEquals("Ana Pérez", u.getNombre());
            assertEquals("ana@test.local", u.getEmail());
        }

        @Test
        void cambiarElEmailPideLaContrasenaYQueNoEsteUsado() {
            assertEquals("Para cambiar el email ingresá tu contraseña actual", assertThrows(IllegalArgumentException.class,
                    () -> service.actualizarPerfil(5L, new DatosPerfil("Ana", "nueva@test.local", null, null))).getMessage());
            assertThrows(IllegalArgumentException.class,
                    () -> service.actualizarPerfil(5L, new DatosPerfil("Ana", "nueva@test.local", null, "otra")));

            when(repo.buscarPorEmail("usado@test.local")).thenReturn(profesional);
            assertThrows(IllegalStateException.class,
                    () -> service.actualizarPerfil(5L, new DatosPerfil("Ana", "usado@test.local", null, "clave1234")));

            Usuario u = service.actualizarPerfil(5L, new DatosPerfil("Ana", "Nueva@Test.local", null, "clave1234"));
            assertEquals("nueva@test.local", u.getEmail());
        }

        @Test
        void validaNombreEmailYUsuario() {
            assertThrows(IllegalArgumentException.class, () -> service.actualizarPerfil(5L, new DatosPerfil(" ", "ana@test.local", null, null)));
            assertThrows(IllegalArgumentException.class, () -> service.actualizarPerfil(5L, new DatosPerfil(null, "ana@test.local", null, null)));
            assertThrows(IllegalArgumentException.class, () -> service.actualizarPerfil(5L, new DatosPerfil("x".repeat(81), "ana@test.local", null, null)));
            assertEquals("El email no es válido", assertThrows(IllegalArgumentException.class,
                    () -> service.actualizarPerfil(5L, new DatosPerfil("Ana", "sin-arroba", null, null))).getMessage());
            assertThrows(IllegalArgumentException.class, () -> service.actualizarPerfil(5L, new DatosPerfil("Ana", null, null, null)));
            assertThrows(IllegalArgumentException.class, () -> service.actualizarPerfil(99L, new DatosPerfil("Ana", "a@b.c", null, null)));
            paciente.setActivo(false);
            assertThrows(IllegalArgumentException.class, () -> service.actualizarPerfil(5L, new DatosPerfil("Ana", "ana@test.local", null, null)));
        }

        @Test
        void soloElProfesionalCambiaLaEspecialidad() {
            assertEquals("Cardiología", service.actualizarPerfil(6L, new DatosPerfil("Dra", "dra@test.local", "cardiología", null)).getEspecialidad());
            assertThrows(IllegalArgumentException.class, () -> service.actualizarPerfil(6L, new DatosPerfil("Dra", "dra@test.local", "Magia", null)));
            assertNull(service.actualizarPerfil(5L, new DatosPerfil("Ana", "ana@test.local", "Cardiología", null)).getEspecialidad());
        }

        @Test
        void especialidadesDeVariosProfesionales() {
            profesional.setEspecialidad("Pediatría");
            when(repo.listarPorIds(Set.of(5L, 6L))).thenReturn(List.of(paciente, profesional));
            java.util.Set<Long> ids = new java.util.HashSet<>(Set.of(5L, 6L));
            ids.add(null);
            assertEquals(Map.of(6L, "Pediatría"), service.especialidadesDe(ids));
            assertEquals(Map.of(), service.especialidadesDe(Set.of()));
        }

        @Test
        void elAltaDeProfesionalGuardaLaEspecialidad() {
            when(repo.buscarPorId(1L)).thenReturn(usuario(1L, RolUsuario.ADMIN, "admin@x.com"));
            when(repo.guardar(any())).thenAnswer(i -> i.getArgument(0));
            Usuario datos = new Usuario();
            datos.setNombre("Dr");
            datos.setEmail("dr@x.com");
            datos.setPassword("temp1234");
            assertEquals(Especialidades.POR_DEFECTO, service.crearProfesional(1L, datos).getEspecialidad());
            datos.setEspecialidad("Dermatología");
            assertEquals("Dermatología", service.crearProfesional(1L, datos).getEspecialidad());
        }

        @Test
        void resetearLaContrasenaAtiendeElPedidoDeRecuperacion() {
            when(repo.buscarPorId(1L)).thenReturn(usuario(1L, RolUsuario.ADMIN, "admin@x.com"));
            paciente.setRecuperacionSolicitadaEn(LocalDateTime.now());
            service.resetearPassword(1L, 5L);
            assertNull(paciente.getRecuperacionSolicitadaEn());
            assertTrue(paciente.isDebeCambiarPassword());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Recuperacion {

        @Mock private UsuarioRepository repo;
        @Mock private INotificacionService notificaciones;
        @InjectMocks private RecuperacionPasswordService service;
        private Usuario paciente;

        @BeforeEach
        void preparar() {
            paciente = usuario(5L, RolUsuario.PACIENTE, "ana@test.local");
            lenient().when(repo.buscarPorEmail("ana@test.local")).thenReturn(paciente);
            lenient().when(repo.buscarPorId(5L)).thenReturn(paciente);
            lenient().when(repo.listarPorRol(RolUsuario.ADMIN))
                    .thenReturn(List.of(usuario(1L, RolUsuario.ADMIN, "a1@x.com"), usuario(2L, RolUsuario.ADMIN, "a2@x.com")));
        }

        @Test
        void avisaACadaAdministradorYMarcaElPedido() {
            service.solicitarPorEmail("  ana@test.local ");

            assertNotNull(paciente.getRecuperacionSolicitadaEn());
            verify(repo).actualizar(paciente);
            verify(notificaciones).avisar(eq(1L), eq("RECUPERAR_PASSWORD"), anyString(), contains("ana@test.local"));
            verify(notificaciones).avisar(eq(2L), eq("RECUPERAR_PASSWORD"), anyString(), contains("paciente"));
        }

        @Test
        void unProfesionalTambienPuedePedirloDesdeElPerfil() {
            Usuario prof = usuario(6L, RolUsuario.PROFESIONAL, "dra@test.local");
            when(repo.buscarPorId(6L)).thenReturn(prof);
            service.solicitarDesdePerfil(6L);
            verify(notificaciones, times(2)).avisar(anyLong(), anyString(), anyString(), contains("profesional"));
        }

        @Test
        void unPedidoRecienteNoVuelveAAvisar() {
            paciente.setRecuperacionSolicitadaEn(LocalDateTime.now().minusMinutes(2));
            service.solicitarDesdePerfil(5L);
            verifyNoInteractions(notificaciones);

            paciente.setRecuperacionSolicitadaEn(LocalDateTime.now().minusMinutes(RecuperacionPasswordService.MINUTOS_ENTRE_PEDIDOS + 1));
            service.solicitarDesdePerfil(5L);
            verify(notificaciones, times(2)).avisar(anyLong(), anyString(), anyString(), anyString());
        }

        @Test
        void cuentasInexistentesInactivasOAdminNoAvisanNiLoRevelan() {
            service.solicitarPorEmail("nadie@test.local");
            paciente.setActivo(false);
            service.solicitarPorEmail("ana@test.local");
            Usuario admin = usuario(1L, RolUsuario.ADMIN, "admin@x.com");
            when(repo.buscarPorEmail("admin@x.com")).thenReturn(admin);
            service.solicitarPorEmail("admin@x.com");
            verifyNoInteractions(notificaciones);
            verify(repo, never()).actualizar(any());
        }

        @Test
        void sinEmailPideIngresarlo() {
            assertEquals("Ingresá tu email", assertThrows(IllegalArgumentException.class, () -> service.solicitarPorEmail(" ")).getMessage());
            assertThrows(IllegalArgumentException.class, () -> service.solicitarPorEmail(null));
        }
    }

    @Nested
    class ListaDeEspecialidades {

        @Test
        void validaContraLaListaSinImportarMayusculas() {
            assertEquals("Pediatría", Especialidades.validar("  pediatría "));
            assertEquals(Especialidades.POR_DEFECTO, Especialidades.validar(null));
            assertEquals(Especialidades.POR_DEFECTO, Especialidades.validar(" "));
            assertThrows(IllegalArgumentException.class, () -> Especialidades.validar("Astrología"));
            assertTrue(Especialidades.LISTA.contains(Especialidades.POR_DEFECTO));
        }
    }
}
