# ADR 0007 · Componentes stateful y stateless

- **Estado:** aceptada (reemplaza la decisión de la Entrega 2)
- **Fecha:** 13/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

La consigna pide justificar qué componentes guardan estado conversacional y cuáles no.

En la Entrega 2, `TurnoService` estaba anotado `@Stateful`, pero en la práctica no guardaba
nada. JAX-RS crea el recurso en cada pedido, así que cada instancia atendía un único pedido
y se destruía (el log mostraba una creación y una destrucción por request). Era `@Stateful`
solo de nombre.

Sacar un turno sí es una **conversación**: el paciente elige un profesional, después
reserva un horario y por último lo confirma, en tres pedidos HTTP distintos.

## Decisión

- **Los servicios de negocio son `@Stateless`:** `TurnoService`, `UsuarioService`,
  `HistoriaClinicaService`, `NotificacionService`, `TelemedicinaService`,
  `FacturacionService` y `ObraSocialService`. No recuerdan nada de un usuario entre
  llamadas, así que el contenedor los administra en un pool y cualquier instancia atiende
  cualquier pedido.
- **El estado conversacional está en un componente propio, `@Stateful`:**
  `AsistenteAgendamiento`. Recuerda el profesional elegido y el turno retenido.
  - La referencia a la instancia se guarda en la **sesión HTTP** (cookie `JSESSIONID`), así
    que cada navegador vuelve a *su* instancia. El frontend manda la cookie solo en esos
    pedidos (`credentials: "include"`).
  - `confirmar()` y `abandonar()` son `@Remove`: terminan la conversación y el contenedor
    destruye la instancia. `confirmar()` usa `retainIfException = true`, para que el
    paciente pueda elegir otro horario si la reserva venció.
  - `@StatefulTimeout(5 minutos)`, lo mismo que dura la reserva temporal, descarta las
    conversaciones abandonadas.
  - Tiene `@PrePassivate` y `@PostActivate` para registrar cuando el contenedor la pasiva.
- **La regla de negocio no depende de la memoria del bean.** La reserva queda persistida en
  el turno (`RESERVADO_TEMPORAL` y `holdExpiraEn`). Si la instancia se pierde, la reserva
  vence igual y el horario vuelve a ofrecerse.

## Alternativas consideradas

- **Mantener `TurnoService` como `@Stateful`.** No cumplía la función y costaba una
  instancia por pedido.
- **Guardar la conversación solo en el navegador.** Es más simple, pero no muestra el
  componente stateful que pide la consigna y obliga a mandar todo el contexto en cada pedido.

## Consecuencias

- Si el paciente sale de "Agendar turno" y vuelve, el servidor le devuelve su reserva en
  curso ("Retomamos tu reserva en curso").
- El asistente solo orquesta: las reglas están en `TurnoService` y en el estado del turno
  (ADR 0001).
- La instancia stateful ocupa memoria mientras dura la conversación, como máximo 5 minutos
  sin actividad.

## Cómo se verifica

- `AsistenteAgendamientoTest`: los pasos de la conversación, la reserva vencida que
  conserva la conversación y el abandono.
- E2E-05 y E2E-06: el flujo completo desde la interfaz.
- En la pantalla de agendar aparece "asistente #N · K pasos", el mismo número en cada
  pedido de la conversación.
