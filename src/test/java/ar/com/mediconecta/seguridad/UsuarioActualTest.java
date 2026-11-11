package ar.com.mediconecta.seguridad;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import jakarta.security.enterprise.CallerPrincipal;
import jakarta.security.enterprise.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsuarioActualTest {

    @Mock
    private SecurityContext securityContext;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private UsuarioActual usuarioActual;

    @Test
    void elIdSaleDelPrincipalDelToken() {
        when(securityContext.getCallerPrincipal()).thenReturn(new CallerPrincipal("15"));
        assertEquals(15L, usuarioActual.id());
        assertTrue(usuarioActual.esElMismo(15L));
        assertFalse(usuarioActual.esElMismo(16L));
    }

    @Test
    void sinSesionResponde401() {
        when(securityContext.getCallerPrincipal()).thenReturn(null);
        NotAuthorizedException e = assertThrows(NotAuthorizedException.class, usuarioActual::id);
        assertEquals(401, e.getResponse().getStatus());
    }

    @Test
    void elRolLoDiceElContenedor() {
        when(securityContext.isCallerInRole(Roles.ADMIN)).thenReturn(true);
        assertTrue(usuarioActual.es(Roles.ADMIN));
        assertFalse(usuarioActual.es(Roles.PACIENTE));
    }

    @Test
    void permitidoNoHaceNada() {
        assertDoesNotThrow(() -> usuarioActual.exigir(true, "motivo"));
    }

    @Test
    void recursoAjenoResponde404ComoSiNoExistiera() {
        when(securityContext.getCallerPrincipal()).thenReturn(new CallerPrincipal("9"));
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/mediconecta/api/turnos/paciente/3");

        NotFoundException e = assertThrows(NotFoundException.class, () -> usuarioActual.exigir(false, "no es suyo"));

        assertEquals(404, e.getResponse().getStatus());
        assertEquals("No encontrado", ((ar.com.mediconecta.config.ApiError) e.getResponse().getEntity()).error);
    }
}
