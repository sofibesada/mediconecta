package ar.com.mediconecta.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** D-16: un caracter NUL llegaba a PostgreSQL y respondia 500. */
class CaracterNulFilterTest {

    private final CaracterNulFilter filtro = new CaracterNulFilter();

    private ContainerRequestContext pedido(String uri, String cuerpo) {
        ContainerRequestContext r = mock(ContainerRequestContext.class);
        UriInfo info = mock(UriInfo.class);
        when(info.getRequestUri()).thenReturn(URI.create(uri));
        when(r.getUriInfo()).thenReturn(info);
        when(r.hasEntity()).thenReturn(cuerpo != null);
        if (cuerpo != null) {
            when(r.getEntityStream()).thenReturn(new ByteArrayInputStream(cuerpo.getBytes(StandardCharsets.UTF_8)));
        }
        return r;
    }

    private static int estadoDelCorte(ContainerRequestContext r) {
        ArgumentCaptor<Response> c = ArgumentCaptor.forClass(Response.class);
        verify(r).abortWith(c.capture());
        return c.getValue().getStatus();
    }

    @Test
    void unCuerpoConElEscapeNulSeRechazaCon400() throws Exception {
        ContainerRequestContext r = pedido("http://x/api/usuarios/login", "{\"email\":\"a\\u0000b@x.com\",\"password\":\"x\"}");
        filtro.filter(r);
        assertEquals(400, estadoDelCorte(r));
    }

    @Test
    void unaUrlConNulSeRechazaCon400() throws Exception {
        ContainerRequestContext r = pedido("http://x/api/turnos/paciente/1%00", null);
        filtro.filter(r);
        assertEquals(400, estadoDelCorte(r));
    }

    @Test
    void unPedidoNormalSigueYSuCuerpoSePuedeLeerOtraVez() throws Exception {
        String json = "{\"email\":\"ana@x.com\",\"password\":\"Pérez123\"}";
        ContainerRequestContext r = pedido("http://x/api/usuarios/login", json);
        filtro.filter(r);
        verify(r, never()).abortWith(any());
        ArgumentCaptor<InputStream> c = ArgumentCaptor.forClass(InputStream.class);
        verify(r).setEntityStream(c.capture());
        assertEquals(json, new String(c.getValue().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void detectaElByteCeroYElEscapeConMayusculas() {
        assertTrue(CaracterNulFilter.tieneNul(new byte[] {'a', 0, 'b'}));
        assertTrue(CaracterNulFilter.tieneNul("\\U0000".getBytes(StandardCharsets.UTF_8)));
        assertFalse(CaracterNulFilter.tieneNul("\\u00e9 normal".getBytes(StandardCharsets.UTF_8)));
    }
}
