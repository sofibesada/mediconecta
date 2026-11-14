package ar.com.mediconecta.historiaclinica.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.historiaclinica.data.AccesoHistoriaClinicaRepository;
import ar.com.mediconecta.historiaclinica.model.AccesoHistoriaClinica;
import ar.com.mediconecta.historiaclinica.model.AccesoHistoriaClinica.Recurso;
import ar.com.mediconecta.historiaclinica.model.AccesoHistoriaClinica.Resultado;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditoriaHistoriaClinicaServiceTest {

    @Mock private AccesoHistoriaClinicaRepository repository;
    @InjectMocks private AuditoriaHistoriaClinicaService service;

    @Test
    void registraQuienQueRecursoDeQuePacienteYCuando() {
        when(repository.guardar(any())).thenAnswer(i -> i.getArgument(0));

        AccesoHistoriaClinica a = service.registrar(6L, "PROFESIONAL", 5L, Recurso.FICHA_PACIENTE, Resultado.AUTORIZADO);

        assertEquals(6L, a.getUsuarioId());
        assertEquals("PROFESIONAL", a.getRol());
        assertEquals(5L, a.getPacienteId());
        assertEquals(Recurso.FICHA_PACIENTE, a.getRecurso());
        assertEquals(Resultado.AUTORIZADO, a.getResultado());
        assertNotNull(a.getFechaHora());
        verify(repository).guardar(a);
    }

    @Test
    void unAccesoDenegadoQuedaComoDenegado() {
        when(repository.guardar(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals(Resultado.DENEGADO,
                service.registrar(7L, "PACIENTE", 5L, Recurso.HISTORIA_CLINICA, Resultado.DENEGADO).getResultado());
    }

    @Test
    void sinDatosCompletosNoRegistra() {
        assertThrows(IllegalArgumentException.class, () -> service.registrar(null, "PACIENTE", 5L, Recurso.FICHA_PACIENTE, Resultado.AUTORIZADO));
        assertThrows(IllegalArgumentException.class, () -> service.registrar(1L, "PACIENTE", null, Recurso.FICHA_PACIENTE, Resultado.AUTORIZADO));
        assertThrows(IllegalArgumentException.class, () -> service.registrar(1L, "PACIENTE", 5L, null, Resultado.AUTORIZADO));
        assertThrows(IllegalArgumentException.class, () -> service.registrar(1L, "PACIENTE", 5L, Recurso.FICHA_PACIENTE, null));
        assertThrows(IllegalArgumentException.class, () -> service.registrar(1L, "HACKER", 5L, Recurso.FICHA_PACIENTE, Resultado.AUTORIZADO));
        assertThrows(IllegalArgumentException.class, () -> service.registrar(1L, null, 5L, Recurso.FICHA_PACIENTE, Resultado.AUTORIZADO));
        verify(repository, never()).guardar(any());
    }

    @Test
    void listaPorPacienteOLasUltimas() {
        AccesoHistoriaClinica a = new AccesoHistoriaClinica();
        when(repository.listarDePaciente(5L, AuditoriaHistoriaClinicaService.MAXIMO_LISTADO)).thenReturn(List.of(a));
        when(repository.listarRecientes(AuditoriaHistoriaClinicaService.MAXIMO_LISTADO)).thenReturn(List.of());

        assertEquals(List.of(a), service.listar(5L));
        assertEquals(List.of(), service.listar(null));
    }

    @Test
    void elRegistroSoloTieneIdentificadoresYFecha() {
        // Si alguien le agrega un campo (por ejemplo, el diagnostico), esta prueba lo frena.
        Set<String> campos = Arrays.stream(AccesoHistoriaClinica.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());
        assertEquals(Set.of("id", "usuarioId", "rol", "pacienteId", "recurso", "resultado", "fechaHora"), campos);
    }
}
