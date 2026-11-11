# ADR 0001 · Patrón State en el turno

- **Estado:** aceptada
- **Fecha:** 11/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

El turno es la entidad central de MediConecta. Pasa por cuatro estados guardados en la
base: `DISPONIBLE`, `RESERVADO_TEMPORAL`, `CONFIRMADO` y `CANCELADO`. Un turno confirmado
cuya fecha ya pasó se considera **finalizado**; no se guarda, se calcula a partir de la fecha.

Qué se puede hacer con un turno depende del estado en el que está:

| Desde \ operación | reservar | confirmar | liberar | cancelar | reprogramar |
|---|---|---|---|---|---|
| DISPONIBLE | → RESERVADO_TEMPORAL (si la fecha no pasó) | ✗ | ✗ | → CANCELADO | cambia la fecha |
| RESERVADO_TEMPORAL | solo si la reserva venció | → CONFIRMADO (si la reserva no venció) | → DISPONIBLE | → CANCELADO | ✗ |
| CONFIRMADO | ✗ | ✗ | ✗ | → CANCELADO (si no finalizó) | cambia la fecha (si no finalizó) |
| CANCELADO | ✗ | ✗ | ✗ | ✗ | ✗ |

✗ = transición inválida. La cancelación además respeta la anticipación mínima de 24 horas,
salvo en las urgencias.

Antes, estas reglas estaban repartidas en `TurnoService` como cadenas de `if` sobre el
estado antes de cada `setEstado(...)`. Una regla nueva obligaba a revisar todos los métodos.
Además, nada impedía que otro código cambiara el estado sin pasar por esas validaciones.

## Decisión

Aplicamos el patrón **State** (paquete `ar.com.mediconecta.turnos.model.estado`):

- `EstadoDelTurno` es la interfaz con las cinco operaciones. Sus métodos por defecto lanzan
  `TransicionInvalidaException`.
- Hay una clase por estado: `Disponible`, `ReservadoTemporal`, `Confirmado` y `Cancelado`.
  Cada una implementa **solo** las transiciones que permite. `Cancelado` no implementa ninguna.
- `Turno` es el contexto. Persiste su estado como enum, porque eso es lo que guarda JPA, y
  delega en el objeto que corresponde: `turno.reservar(...)`, `turno.confirmar()`,
  `turno.liberar()`, `turno.cancelar(politica)` y `turno.reprogramar(fecha)`.
- `TransicionInvalidaException` extiende `IllegalStateException` y es
  `@ApplicationException(rollback = false)`. El mapper la traduce a **409 Conflict** con un
  JSON `{"error": "..."}` que explica por qué.
- La regla de anticipación de 24 horas sigue siendo una **Strategy**
  (`PoliticaCancelacionEstandar` y `PoliticaCancelacionUrgencia`). El servicio la elige y se
  la pasa al estado.
- En `TurnoService` quedan solo las reglas que no son del estado: que el paciente exista y
  esté activo, que la fecha sea futura, que el horario no choque con otro, la obra social,
  los eventos y la facturación.

Los estados no tienen datos propios, así que hay una sola instancia de cada uno.

## Alternativas consideradas

- **Dejar los `if` en el servicio.** Es lo más simple, pero las reglas quedan duplicadas y
  dispersas, y no hay un único lugar donde leer qué transiciones son válidas.
- **Comportamiento dentro del enum** (cada constante de `EstadoTurno` con sus métodos). Es más
  compacto, pero mezcla lo que se persiste con el comportamiento y deja un enum muy largo.
  Separar las clases además facilita probar cada estado por su cuenta.
- **Una librería de máquina de estados** (por ejemplo, Spring Statemachine). Es demasiado
  para cuatro estados y agrega una dependencia que no corre de forma natural en Jakarta EE.

## Consecuencias

- Una transición inválida siempre responde **409** con un mensaje claro, venga de donde
  venga el pedido: la API REST o el asistente de agendamiento (`@Stateful`).
- Agregar un estado (por ejemplo, `AUSENTE` si el paciente no se presenta) es una clase nueva
  más una línea en `EstadoDelTurno.de(...)`, sin tocar los demás estados.
- `setEstado(...)` sigue siendo público porque lo necesita JPA. Por convención solo lo usan
  las clases de estado y la creación de un horario nuevo (`DISPONIBLE`).
- La seguridad se evalúa antes que el estado: si el turno no es del usuario, la respuesta es
  **404** (no se revela que existe) aunque la transición también fuera inválida.

## Cómo se verifica

- `EstadoDelTurnoTest`: la tabla completa de transiciones, 20 combinaciones de estado y
  operación, más los casos límite (reserva vencida, turno finalizado, política de 24 horas).
- `TurnoServiceTest`: el servicio delega en el estado y propaga el 409.
- Contra la API levantada: cada transición inválida responde 409 y las válidas, 200 o 204.
