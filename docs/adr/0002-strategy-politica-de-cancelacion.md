# ADR 0002 · Strategy para la política de cancelación

- **Estado:** aceptada
- **Fecha:** 13/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

Un turno se puede cancelar o no según cuánta anticipación haya. La regla general exige
**24 horas** antes del turno. Un turno marcado como **urgente** se puede cancelar en cualquier
momento.

Es una regla que va a cambiar: las clínicas suelen pedir variantes, como 48 horas para
especialistas o sin restricción para el administrador. Si la regla se escribe con `if`
dentro de `TurnoService`, cada variante nueva obliga a modificar el servicio y a volver a
probar todo el método de cancelación.

## Decisión

Aplicamos el patrón **Strategy** (paquete `ar.com.mediconecta.turnos.business`):

- `PoliticaCancelacion` es la interfaz, con un solo método: `boolean puedeCancelarse(Turno)`.
- `PoliticaCancelacionEstandar` exige 24 horas o más entre ahora y la fecha del turno.
- `PoliticaCancelacionUrgencia` siempre permite cancelar.
- `TurnoService.cancelarTurno` elige la estrategia según `turno.isUrgente()` y se la pasa
  al estado del turno: `turno.cancelar(politica::puedeCancelarse)` (ver ADR 0001).
- Si la política no lo permite, el estado lanza `TransicionInvalidaException` con
  "No se puede cancelar: faltan menos de 24hs para el turno", y la API responde **409**.

## Alternativas consideradas

- **Un `if` en el servicio.** Es más corto hoy, pero mezcla la regla con el flujo de la
  cancelación y cada variante agranda el método.
- **Un número de horas configurable.** Cubre "24 o 48 horas", pero no reglas de otra forma,
  como la urgencia o una excepción por rol.

## Consecuencias

- Una política nueva es una clase nueva más el criterio para elegirla, sin tocar las demás.
- Cada política se prueba sola, sin base de datos ni contenedor.
- La regla de *cuándo* se puede cancelar (Strategy) queda separada de *desde qué estado* se
  puede cancelar (State, ADR 0001): las dos se combinan en `turno.cancelar(...)`.

## Cómo se verifica

- `RecordatoriosYAgendaTest.politicasDeCancelacion`: con 5 horas de anticipación la estándar
  rechaza y la de urgencia acepta.
- `EstadoDelTurnoTest.laPoliticaDeAnticipacionPuedeImpedirLaCancelacion`: el estado respeta
  lo que decide la política.
- E2E-07 (`e2e/tests/turnos.spec.ts`): un paciente no puede cancelar un turno que es en 5 horas.
