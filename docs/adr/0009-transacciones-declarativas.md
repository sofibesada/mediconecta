# ADR 0009 · Transacciones declarativas (JTA)

- **Estado:** aceptada
- **Fecha:** 13/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

Confirmar un turno toca varios recursos a la vez:

1. cambia el estado del turno en PostgreSQL;
2. pide la autorización a la obra social (SOAP);
3. guarda el copago y el código de autorización;
4. publica el evento en el tópico JMS (ADR 0005);
5. registra el reclamo y lo envía a la cola de facturación (ADR 0006).

Si falla un paso en el medio, el sistema no puede quedar a medias, por ejemplo con el turno
confirmado sin reclamo o con el aviso enviado de un turno que no se confirmó. Escribir los
`commit` y `rollback` a mano en cada método es propenso a errores.

## Decisión

Usamos **transacciones declarativas** (CMT/JTA) gestionadas por el contenedor:

- **`REQUIRED`** (el valor por defecto de los EJB): cada operación de negocio abre una
  transacción o se suma a la que ya existe.
- **XA entre la base y el broker.** Las connection factories de JMS son `transaction=xa`
  (`docker/wildfly/configurar.cli`), así que el envío de mensajes **hace commit o rollback
  junto con la base**.
- **`MANDATORY`** en `TurnoEventoPublisher` y `FacturacionColaPublisher`: solo se pueden
  llamar dentro de una transacción. Es imposible publicar un evento "suelto".
- **`REQUIRES_NEW`** en `AuditoriaAutorizacionService.registrar`: el intento de
  autorización se guarda en su **propia** transacción. Así queda registrado aunque la
  confirmación se revierta, por ejemplo si la obra social rechazó o no respondió.
- **Las excepciones deciden el rollback:**

| Excepción | Tipo | Efecto |
|---|---|---|
| `AutorizacionRechazadaException` | `@ApplicationException(rollback = true)` | Revierte todo: el turno sigue reservado y se puede confirmar como particular |
| `TransicionInvalidaException`, `ConflictoTurnoException`, `DatosTurnoInvalidosException` | `@ApplicationException(rollback = false)` | Llega tal cual al cliente (409/400); por ejemplo, la liberación de una reserva vencida **sí** se guarda |
| `ObrasSocialesNoDisponibleException`, fallas de JMS | De sistema | Rollback; en un MDB, además, Artemis reentrega el mensaje |

## Alternativas consideradas

- **Transacciones programáticas (BMT).** Dan más control, pero hay que repetir el manejo en
  cada método y es fácil olvidarse un rollback.
- **Publicar el evento después del commit (outbox o `@Observes(AFTER_SUCCESS)`).** Evita
  XA, pero hace falta una tabla de salida y un proceso que la vacíe. XA ya viene en WildFly
  y Artemis.

## Consecuencias

- Una confirmación es **todo o nada**: o se guarda el turno con su evento y su reclamo, o
  no se guarda nada.
- Con el broker caído, la confirmación falla en unos 3 s con **503** ("No se guardó ningún
  cambio"), en lugar de confirmar sin avisos.
- La auditoría de autorizaciones conserva también los rechazos y las faltas de respuesta.
- XA cuesta más que una transacción local. Para el volumen de una clínica no es un
  problema.

## Cómo se verifica

- `TurnoServiceTest`: el orden de los pasos de la confirmación, el rechazo de la obra
  social y la liberación de una reserva vencida.
- `ObrasSocialesBusinessTest`: la auditoría se registra con cada resultado.
- `ExceptionMappersTest`: cada excepción responde con el código HTTP correcto (409, 400,
  503).
- E2E-06: después del rechazo de la obra social, el turno sigue reservado y se confirma
  como particular.
