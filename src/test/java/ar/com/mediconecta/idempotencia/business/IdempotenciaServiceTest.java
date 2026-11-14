package ar.com.mediconecta.idempotencia.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.idempotencia.data.IdempotenciaRepository;
import ar.com.mediconecta.idempotencia.model.RegistroIdempotencia;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdempotenciaServiceTest {

    private static final Long USUARIO = 7L;
    private static final String OPERACION = "POST /turnos/3/confirmar?particular=false";

    @Mock private IdempotenciaRepository repository;
    @InjectMocks private IdempotenciaService service;

    private final AtomicInteger ejecuciones = new AtomicInteger();

    private Object accion() {
        ejecuciones.incrementAndGet();
        return Map.of("id", 3, "estado", "CONFIRMADO");
    }

    private static RegistroIdempotencia registro(String operacion, String json) {
        RegistroIdempotencia r = new RegistroIdempotencia();
        r.setClave("clave-1");
        r.setUsuarioId(USUARIO);
        r.setOperacion(operacion);
        r.setRespuestaJson(json);
        r.setCreadoEn(LocalDateTime.now());
        return r;
    }

    @Test
    void sinClaveSeEjecutaSinGuardarNada() {
        IdempotenciaService.Resultado r = service.ejecutar(null, USUARIO, OPERACION, this::accion);
        IdempotenciaService.Resultado r2 = service.ejecutar("  ", USUARIO, OPERACION, this::accion);

        assertEquals(2, ejecuciones.get());
        assertFalse(r.repetido());
        assertFalse(r2.repetido());
        verifyNoInteractions(repository);
    }

    @Test
    void laPrimeraVezEjecutaYGuardaLaRespuesta() {
        IdempotenciaService.Resultado r = service.ejecutar(" clave-1 ", USUARIO, OPERACION, this::accion);

        assertEquals(1, ejecuciones.get());
        assertFalse(r.repetido());
        ArgumentCaptor<RegistroIdempotencia> guardado = ArgumentCaptor.forClass(RegistroIdempotencia.class);
        verify(repository).guardar(guardado.capture());
        assertEquals("clave-1", guardado.getValue().getClave());
        assertEquals(USUARIO, guardado.getValue().getUsuarioId());
        assertEquals(OPERACION, guardado.getValue().getOperacion());
        assertTrue(guardado.getValue().getRespuestaJson().contains("CONFIRMADO"));
        assertTrue(guardado.getValue().getCreadoEn() != null);
    }

    @Test
    void conLaMismaClaveDevuelveLaRespuestaGuardadaSinEjecutar() {
        when(repository.buscar(eq("clave-1"), eq(USUARIO), any())).thenReturn(registro(OPERACION, "{\"id\":3}"));

        IdempotenciaService.Resultado r = service.ejecutar("clave-1", USUARIO, OPERACION, this::accion);

        assertEquals(0, ejecuciones.get());
        assertTrue(r.repetido());
        assertEquals("{\"id\":3}", r.json());
        assertNull(r.cuerpo());
        verify(repository, never()).guardar(any());
    }

    @Test
    void laMismaClaveEnOtraOperacionSeRechaza() {
        when(repository.buscar(anyString(), anyLong(), any())).thenReturn(registro("POST /turnos/9/confirmar?particular=false", "{}"));

        assertThrows(ClaveReutilizadaException.class, () -> service.ejecutar("clave-1", USUARIO, OPERACION, this::accion));
        assertEquals(0, ejecuciones.get());
    }

    @Test
    void lasClavesValen24Horas() {
        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        service.respuestaPrevia("clave-1", USUARIO, OPERACION);

        verify(repository).buscar(eq("clave-1"), eq(USUARIO), desde.capture());
        // El limite es "ahora menos 24 horas" (con un minuto de margen por el tiempo de la prueba).
        LocalDateTime esperado = LocalDateTime.now().minusHours(IdempotenciaService.HORAS_DE_VALIDEZ);
        assertFalse(desde.getValue().isBefore(esperado.minusMinutes(1)), "La ventana es mayor a 24 horas");
        assertFalse(desde.getValue().isAfter(esperado.plusMinutes(1)), "La ventana es menor a 24 horas");
    }

    @Test
    void unaClaveDemasiadoLargaSeRechaza() {
        String larga = "x".repeat(IdempotenciaService.LARGO_MAXIMO + 1);
        assertThrows(IllegalArgumentException.class, () -> service.ejecutar(larga, USUARIO, OPERACION, this::accion));
        assertThrows(IllegalArgumentException.class, () -> service.registrar(larga, USUARIO, OPERACION, "x"));
        assertEquals(0, ejecuciones.get());
    }

    @Test
    void enDosPasosSinClaveNoHaceNada() {
        assertNull(service.respuestaPrevia(null, USUARIO, OPERACION));
        service.registrar("", USUARIO, OPERACION, "x");
        verifyNoInteractions(repository);
    }
}
