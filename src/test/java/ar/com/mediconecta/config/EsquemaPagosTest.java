package ar.com.mediconecta.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.com.mediconecta.pagos.model.Pago;
import org.junit.jupiter.api.Test;

class EsquemaPagosTest {

    @Test
    void laRestriccionDeLaBaseIncluyeTodosLosEstadosDelPago() {
        // Si alguien agrega un estado al enum y no a la restriccion, guardar ese pago fallaria en la base.
        for (Pago.Estado estado : Pago.Estado.values()) {
            assertTrue(ActualizacionEsquemaPagos.CON_RESTRICCION.contains("'" + estado.name() + "'"),
                    "falta " + estado + " en la restriccion de la tabla pago");
            assertTrue(ActualizacionEsquemaPagos.ESTADOS.contains("'" + estado.name() + "'"));
        }
    }
}
