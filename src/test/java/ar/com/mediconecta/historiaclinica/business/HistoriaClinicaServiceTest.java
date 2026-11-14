package ar.com.mediconecta.historiaclinica.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.mediconecta.historiaclinica.data.HistoriaClinicaRepository;
import ar.com.mediconecta.historiaclinica.model.Diagnostico;
import ar.com.mediconecta.historiaclinica.model.HistoriaClinica;
import ar.com.mediconecta.historiaclinica.model.Receta;
import ar.com.mediconecta.usuarios.business.IUsuarioService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoriaClinicaServiceTest {

    @Mock private HistoriaClinicaRepository repository;
    @Mock private IUsuarioService usuarioService;

    @InjectMocks private HistoriaClinicaService service;

    @Test
    void laHistoriaJuntaDiagnosticosYRecetas() {
        when(usuarioService.existeUsuario(1L)).thenReturn(true);
        when(repository.buscarDiagnosticosPorPaciente(1L)).thenReturn(List.of(new Diagnostico()));
        when(repository.buscarRecetasPorPaciente(1L)).thenReturn(List.of(new Receta(), new Receta()));

        HistoriaClinica h = service.consultarHistoria(1L);

        assertEquals(1L, h.getPacienteId());
        assertEquals(1, h.getDiagnosticos().size());
        assertEquals(2, h.getRecetas().size());
        service.init();
        service.destroy();
    }

    @Test
    void registraDiagnosticoConFechaYPaciente() {
        when(usuarioService.existeUsuario(1L)).thenReturn(true);
        Diagnostico d = new Diagnostico();
        d.setDescripcion("Hipertension");

        service.registrarDiagnostico(1L, d);

        assertEquals(1L, d.getPacienteId());
        assertNotNull(d.getFecha());
        verify(repository).guardarDiagnostico(d);
    }

    @Test
    void registraRecetaConFechaYPaciente() {
        when(usuarioService.existeUsuario(1L)).thenReturn(true);
        Receta r = new Receta();
        r.setMedicamento("Enalapril");

        service.registrarReceta(1L, r);

        assertEquals(1L, r.getPacienteId());
        assertNotNull(r.getFecha());
        verify(repository).guardarReceta(r);
    }

    @Test
    void datosObligatorios() {
        when(usuarioService.existeUsuario(1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.registrarDiagnostico(1L, new Diagnostico()));
        Diagnostico vacio = new Diagnostico();
        vacio.setDescripcion("  ");
        assertThrows(IllegalArgumentException.class, () -> service.registrarDiagnostico(1L, vacio));
        assertThrows(IllegalArgumentException.class, () -> service.registrarReceta(1L, new Receta()));
        Receta sinRemedio = new Receta();
        sinRemedio.setMedicamento("");
        assertThrows(IllegalArgumentException.class, () -> service.registrarReceta(1L, sinRemedio));
        verify(repository, never()).guardarDiagnostico(any());
        verify(repository, never()).guardarReceta(any());
    }

    @Test
    void largosMaximos() {
        when(usuarioService.existeUsuario(1L)).thenReturn(true);
        Diagnostico largo = new Diagnostico();
        largo.setDescripcion("x".repeat(HistoriaClinicaService.MAX_DIAGNOSTICO + 1));
        assertThrows(IllegalArgumentException.class, () -> service.registrarDiagnostico(1L, largo));

        Receta remedioLargo = new Receta();
        remedioLargo.setMedicamento("x".repeat(HistoriaClinicaService.MAX_MEDICAMENTO + 1));
        assertThrows(IllegalArgumentException.class, () -> service.registrarReceta(1L, remedioLargo));

        Receta indicacionesLargas = new Receta();
        indicacionesLargas.setMedicamento("Enalapril 10 mg");
        indicacionesLargas.setIndicaciones("x".repeat(HistoriaClinicaService.MAX_INDICACIONES + 1));
        assertThrows(IllegalArgumentException.class, () -> service.registrarReceta(1L, indicacionesLargas));
        verify(repository, never()).guardarDiagnostico(any());
        verify(repository, never()).guardarReceta(any());

        Diagnostico justo = new Diagnostico();
        justo.setDescripcion("x".repeat(HistoriaClinicaService.MAX_DIAGNOSTICO));
        service.registrarDiagnostico(1L, justo);
        Receta sinIndicaciones = new Receta();
        sinIndicaciones.setMedicamento("Enalapril 10 mg");
        service.registrarReceta(1L, sinIndicaciones);
        verify(repository).guardarDiagnostico(justo);
        verify(repository).guardarReceta(sinIndicaciones);
    }

    @Test
    void pacienteInexistenteODesactivado() {
        when(usuarioService.existeUsuario(9L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.consultarHistoria(9L));
        assertThrows(IllegalArgumentException.class, () -> service.registrarDiagnostico(9L, new Diagnostico()));
        assertThrows(IllegalArgumentException.class, () -> service.registrarReceta(9L, new Receta()));
    }
}
