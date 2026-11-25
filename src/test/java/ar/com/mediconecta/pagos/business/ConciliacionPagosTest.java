package ar.com.mediconecta.pagos.business;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.pagos.data.PagoRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConciliacionPagosTest {

    @Mock private PagoRepository repository;
    @Mock private IntentosPago intentos;
    @InjectMocks private ConciliacionPagos conciliacion;

    @AfterEach
    void limpiar() {
        System.clearProperty("mediconecta.pagos.horasDuplicados");
    }

    @Test
    void conciliaLosPendientesYBuscaDuplicadosAunqueUnoFalle() {
        when(repository.idsPendientes()).thenReturn(List.of(1L, 2L));
        when(intentos.conciliar(1L)).thenThrow(new RuntimeException("pasarela caída"));
        when(repository.turnosPagadosDesde(any())).thenReturn(List.of(30L, 31L));
        when(intentos.devolverDuplicados(30L)).thenThrow(new RuntimeException("pasarela caída"));

        conciliacion.conciliarPendientes();

        verify(intentos).conciliar(2L);
        verify(intentos).devolverDuplicados(31L);
    }

    @Test
    void revisaLosTurnosPagadosEnLasUltimas48HorasSalvoQueSeConfigureOtraCosa() {
        when(repository.idsPendientes()).thenReturn(List.of());
        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        when(repository.turnosPagadosDesde(desde.capture())).thenReturn(List.of());

        conciliacion.conciliarPendientes();
        System.setProperty("mediconecta.pagos.horasDuplicados", "2");
        conciliacion.conciliarPendientes();

        LocalDateTime ahora = LocalDateTime.now();
        assertCerca(ahora.minusHours(48), desde.getAllValues().get(0));
        assertCerca(ahora.minusHours(2), desde.getAllValues().get(1));
    }

    private static void assertCerca(LocalDateTime esperado, LocalDateTime obtenido) {
        java.time.ZoneId zona = java.time.ZoneId.systemDefault();
        long segundos = java.time.Duration.between(esperado.atZone(zona).toInstant(), obtenido.atZone(zona).toInstant()).toSeconds();
        org.junit.jupiter.api.Assertions.assertTrue(Math.abs(segundos) < 60,
                "esperado cerca de " + esperado + " y fue " + obtenido);
    }
}
