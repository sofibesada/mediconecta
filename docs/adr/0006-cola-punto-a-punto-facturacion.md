# ADR 0006 · Cola punto a punto para la facturación a obras sociales

- **Estado:** aceptada
- **Fecha:** 13/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

Cuando un paciente confirma un turno con su obra social, hay que **reclamarle a la obra
social** su parte del importe. El reclamo se registra en el sistema legado (SOAP). Ese
sistema puede estar lento o caído, y el paciente no necesita esperar ese trámite para tener
su turno confirmado.

A diferencia de los eventos de turnos (ADR 0005), cada reclamo es **una tarea que debe
hacerse exactamente una vez**: no es un aviso para varios interesados.

## Decisión

Usamos una **cola JMS punto a punto**, `FacturacionQueue` (`java:/jms/queue/Facturacion`):

1. Al confirmar, `FacturacionService.registrarPrestacion` guarda un `Reclamo` en estado
   `PENDIENTE` y `FacturacionColaPublisher` envía `{reclamoId, turnoId}` a la cola. Las dos
   cosas van en la **misma transacción XA** que la confirmación (`MANDATORY`): si algo
   falla, no queda ni el reclamo ni el mensaje.
2. `FacturacionMDB` consume la cola. Cada mensaje lo recibe **un solo consumidor**.
   Procesa el reclamo con el legado y lo marca `ENVIADO` o `RECHAZADO`.
3. Si el legado no responde, la excepción de sistema sale del MDB: el contenedor hace
   rollback y Artemis **reentrega** el mensaje (3 intentos, 2 s entre cada uno). Después
   lo manda a la **DLQ**, desde donde se puede reprocesar con la consola de Artemis.
4. El procesamiento es **idempotente**: un reclamo que ya no está `PENDIENTE` no se vuelve
   a enviar. Además, el legado recibe la referencia del turno.

## Alternativas consideradas

- **Llamar al legado dentro de la confirmación.** El paciente esperaría al sistema de
  facturación, y una caída del legado impediría confirmar turnos.
- **Un tópico.** Con varios suscriptores, el mismo reclamo podría procesarse más de una vez.
- **Un proceso periódico (`@Schedule`) que busque reclamos pendientes.** Funciona, pero
  agrega una demora fija y hay que programar los reintentos a mano. La cola ya los da.

## Consecuencias

- Confirmar un turno no depende de la disponibilidad del sistema de facturación del legado.
- Un reclamo que falla tres veces no se pierde: queda en la DLQ con su contenido, para
  analizarlo o reprocesarlo.
- El administrador ve en "Facturación" el estado de cada reclamo y de cada autorización.

## Cómo se verifica

- `FacturacionServiceTest`: el reclamo nace pendiente y se encola, se procesa aceptado o
  rechazado, y un mensaje repetido no se vuelve a facturar.
- Prueba manual (sección "Simular fallas" del README): con `obrassociales.mock.fallar` el
  legado responde con SOAP Fault, el mensaje se reintenta 3 veces y termina en la DLQ.
- Pendiente: una prueba automatizada del camino de reintentos y DLQ.
