package ar.com.mediconecta.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.usuarios.business.IUsuarioService;
import ar.com.mediconecta.usuarios.model.RolUsuario;
import ar.com.mediconecta.usuarios.model.Usuario;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationMechanismTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private IUsuarioService usuarioService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpMessageContext contexto;

    @InjectMocks
    private JwtAuthenticationMechanism mecanismo;

    @Test
    void sinTokenEnRecursoPublicoEsAnonimo() {
        when(contexto.isProtected()).thenReturn(false);
        when(contexto.doNothing()).thenReturn(AuthenticationStatus.NOT_DONE);

        assertEquals(AuthenticationStatus.NOT_DONE, mecanismo.validateRequest(request, response, contexto));
    }

    @Test
    void sinTokenEnRecursoProtegidoResponde401ConCors() {
        when(contexto.isProtected()).thenReturn(true);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getHeader("Origin")).thenReturn("http://localhost:5173");
        when(contexto.responseUnauthorized()).thenReturn(AuthenticationStatus.SEND_FAILURE);

        assertEquals(AuthenticationStatus.SEND_FAILURE, mecanismo.validateRequest(request, response, contexto));
        verify(response).setHeader("Access-Control-Allow-Origin", "http://localhost:5173");
    }

    @Test
    void conTokenInvalidoEnRecursoProtegidoResponde401() {
        when(request.getHeader("Authorization")).thenReturn("Bearer roto");
        when(tokenService.validar("roto")).thenReturn(null);
        when(contexto.isProtected()).thenReturn(true);
        when(contexto.responseUnauthorized()).thenReturn(AuthenticationStatus.SEND_FAILURE);

        assertEquals(AuthenticationStatus.SEND_FAILURE, mecanismo.validateRequest(request, response, contexto));
        verify(response).setHeader("Access-Control-Allow-Origin", "*");
    }

    @Test
    void conTokenDeCuentaDesactivadaEnRecursoPublicoEsAnonimo() {
        when(request.getHeader("Authorization")).thenReturn("Bearer ok");
        when(tokenService.validar("ok")).thenReturn(4L);
        when(usuarioService.buscarParaSesion(4L)).thenReturn(null);
        when(contexto.isProtected()).thenReturn(false);
        when(contexto.doNothing()).thenReturn(AuthenticationStatus.NOT_DONE);

        assertEquals(AuthenticationStatus.NOT_DONE, mecanismo.validateRequest(request, response, contexto));
    }

    @Test
    void conTokenValidoInformaUsuarioYRol() {
        when(request.getHeader("Authorization")).thenReturn("Bearer ok");
        when(tokenService.validar("ok")).thenReturn(4L);
        when(usuarioService.buscarParaSesion(4L)).thenReturn(usuario(4L, RolUsuario.PROFESIONAL, false));
        when(contexto.notifyContainerAboutLogin(any(java.security.Principal.class), any())).thenReturn(AuthenticationStatus.SUCCESS);

        assertEquals(AuthenticationStatus.SUCCESS, mecanismo.validateRequest(request, response, contexto));
        verify(contexto).notifyContainerAboutLogin(argThat((java.security.Principal p) -> p.getName().equals("4")), eq(Set.of("PROFESIONAL")));
    }

    @Test
    void conContrasenaTemporalEntraSinRol() {
        when(request.getHeader("Authorization")).thenReturn("Bearer ok");
        when(tokenService.validar("ok")).thenReturn(8L);
        when(usuarioService.buscarParaSesion(8L)).thenReturn(usuario(8L, RolUsuario.PROFESIONAL, true));
        when(contexto.notifyContainerAboutLogin(any(java.security.Principal.class), any())).thenReturn(AuthenticationStatus.SUCCESS);

        mecanismo.validateRequest(request, response, contexto);

        verify(contexto).notifyContainerAboutLogin(argThat((java.security.Principal p) -> p.getName().equals("8")), eq(Set.of()));
    }

    private static Usuario usuario(Long id, RolUsuario rol, boolean debeCambiar) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setRol(rol);
        u.setDebeCambiarPassword(debeCambiar);
        return u;
    }
}
