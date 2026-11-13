# ADR 0005 · Publish/subscribe para los eventos de turnos

- **Estado:** aceptada
- **Fecha:** 13/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

Cuando un turno se confirma, cancela o reprograma, otras partes del sistema tienen que
enterarse:

- **notificaciones** avisa al paciente y al profesional;
- **telemedicina** crea o cierra la sala de videollamada de los turnos virtuales;
- **recordatorios** genera el aviso del día anterior.

Si `TurnoService` llamara a cada una, quedaría acoplado a todas. Además, el paciente
esperaría a que se generen los avisos y la sala, y una falla en cualquiera de ellas haría
fallar la confirmación del turno.

## Decisión

Usamos **mensajería publish/subscribe con JMS** sobre ActiveMQ Artemis:

- **Productor.** `TurnoEventoPublisher` publica un `TurnoEvento` en JSON en el tópico
  `TurnosEventosTopic` (`java:/jms/topic/TurnosEventos`). Cada evento lleva un `eventoId`
  (UUID) y la propiedad `tipo`, que se usa para filtrar.
- **El envío es parte de la transacción** del cambio del turno (`MANDATORY`, conexión XA):
  si la confirmación hace rollback, el evento no se publica. Ver ADR 0009.
- **Suscriptores**, cada uno con su **suscripción durable**:
  - `NotificacionTurnoMDB` recibe todos los eventos.
  - `TelemedicinaMDB` usa el *message selector* `tipo IN ('TURNO_CONFIRMADO', 'TURNO_CANCELADO')`,
    así el broker solo le entrega lo que le interesa.
- **Durable:** si la aplicación está caída o redesplegándose cuando se publica un evento,
  Artemis lo guarda y lo entrega cuando vuelve.
- **Consumidores idempotentes:** una notificación es única por `(evento_id, usuario_id)`
  (restricción única en la tabla y control previo en `NotificacionService`). Si el broker
  entrega dos veces el mismo evento, no se duplica el aviso.
- **Reintentos y DLQ:** si un consumidor falla con una excepción de sistema, el contenedor
  hace rollback y Artemis reentrega el mensaje (3 intentos, con 2 s entre cada uno).
  Después lo manda a la cola `DLQ` (`docker/artemis/broker.xml`).

## Alternativas consideradas

- **Llamadas directas entre servicios.** Es más simple, pero acopla todo y hace que la
  confirmación dependa de la telemedicina.
- **Una cola por consumidor.** Obliga al productor a conocer a cada consumidor y enviar
  N mensajes.
- **Eventos CDI (`@Observes`).** Son en memoria: se pierden si la aplicación se cae y no
  tienen reintentos.

## Consecuencias

- Sumar un consumidor nuevo es otro MDB con su suscripción, sin tocar `TurnoService`. Así
  se sumó `TelemedicinaMDB`.
- Los avisos llegan de forma asincrónica, en uno o dos segundos. El frontend los consulta
  cada 10 segundos y vuelve a pedirlos enseguida después de confirmar o cancelar.
- Con el broker caído no se puede confirmar un turno: el productor falla en 3 segundos, la
  transacción se revierte y la API responde 503 sin guardar cambios.

## Cómo se verifica

- `NotificacionServiceTest`: un evento repetido no duplica avisos y cada tipo genera los
  avisos correctos.
- `TelemedicinaServiceTest`: el consumidor de telemedicina procesa confirmados y cancelados.
- En el log de WildFly, el productor corre en el hilo del pedido HTTP (`default task-N`) y
  los consumidores en hilos del broker.
- Prueba manual del README: con el broker apagado la confirmación responde 503 en unos 3 s.
