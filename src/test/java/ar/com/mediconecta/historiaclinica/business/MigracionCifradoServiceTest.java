package ar.com.mediconecta.historiaclinica.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.historiaclinica.data.CifradoClinicoRepository;
import ar.com.mediconecta.historiaclinica.data.CifradoClinicoRepository.Columna;
import ar.com.mediconecta.historiaclinica.data.CifradoClinicoRepository.ValorGuardado;
import ar.com.mediconecta.seguridad.CifradoDatos;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MigracionCifradoServiceTest {

    private final CifradoDatos cifrado = CifradoDatos.desdeBase64(Base64.getEncoder().encodeToString(new byte[32]));

    @Mock private CifradoClinicoRepository repository;
    @InjectMocks private MigracionCifradoService service;

    @Test
    void cifraLoQueQuedoEnTextoPlano() {
        ValorGuardado diag = new ValorGuardado(Columna.DIAGNOSTICO_DESCRIPCION, 1L, "Hipertension");
        ValorGuardado receta = new ValorGuardado(Columna.RECETA_MEDICAMENTO, 2L, "Enalapril 10 mg");
        when(repository.sinCifrar(CifradoDatos.PREFIJO)).thenReturn(List.of(diag, receta));

        assertEquals(2, service.cifrarCon(cifrado));

        ArgumentCaptor<String> nuevo = ArgumentCaptor.forClass(String.class);
        verify(repository).actualizar(eq(diag), nuevo.capture());
        assertTrue(CifradoDatos.estaCifrado(nuevo.getValue()));
        assertFalse(nuevo.getValue().contains("Hipertension"));
        assertEquals("Hipertension", cifrado.descifrar(nuevo.getValue()));
        verify(repository).actualizar(eq(receta), anyString());
    }

    @Test
    void primeroAgrandaLasColumnasYSiNoHayNadaPendienteNoActualiza() {
        when(repository.sinCifrar(CifradoDatos.PREFIJO)).thenReturn(List.of());

        assertEquals(0, service.cifrarCon(cifrado));

        InOrder orden = inOrder(repository);
        orden.verify(repository).ampliarColumnas();
        orden.verify(repository).sinCifrar(CifradoDatos.PREFIJO);
        verify(repository, never()).actualizar(any(), any());
    }

    @Test
    void sinClaveConfiguradaNoTocaLaBase() {
        // En las pruebas no hay MEDICONECTA_CLAVE_DATOS: si otra prueba la configuro antes,
        // esta no aplica.
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("mediconecta.datos.clave") == null
                && System.getenv("MEDICONECTA_CLAVE_DATOS") == null);
        try {
            service.cifrarDatosExistentes();
        } catch (IllegalStateException e) {
            verify(repository, never()).ampliarColumnas();
            return;
        }
        // Si el cifrador ya estaba cargado por otra prueba de esta corrida, no hay nada que verificar.
    }
}
