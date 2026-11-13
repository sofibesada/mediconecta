# ADR 0012 · Contrato HTTP de la API (Problem Details, ETag, Idempotency-Key) y protección XXE

- **Estado:** aceptada
- **Fecha:** 13/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

Para la entrega del 30/11 la API tenía que cumplir cuatro requisitos más:

- **Errores con un formato estándar.** Hasta ahora eran `{"error": "..."}`, un formato propio.
- **Control de concurrencia.** Si dos personas modifican el mismo turno, la segunda no
  puede pisar el cambio de la primera sin enterarse.
- **Pedidos repetibles sin efectos dobles.** Si se corta la conexión al confirmar un turno
  y el cliente reintenta, el turno no se puede confirmar ni facturar dos veces.
- **Protección XXE** en el servicio SOAP: un XML con entidades externas puede leer archivos
  del servidor o hacer pedidos a la red interna.

Además, el broker guardaba sus mensajes dentro del contenedor: se perdían con
`docker compose down`.

## Decisión

**Problem Details (RFC 9457).**
- Todo error sale con `Content-Type: application/problem+json` y los campos `type`
  (`about:blank`), `title`, `status` y `detail`.
- Un solo punto los arma: `ApiError.respuesta(...)`, que usan todos los `ExceptionMapper`, el
  login y las páginas de error 401/403 del contenedor.
- Se mantiene `error` (igual a `detail`) como miembro de extensión: el frontend, las pruebas
  y las colecciones de Postman lo siguen leyendo sin cambios.

**ETag e If-Match.**
- El ETag de un turno es su versión (`@Version` de JPA). Va en `GET /turnos/{id}` (nuevo) y en
  cada respuesta que devuelve un turno.
- `GET` con `If-None-Match` igual a la versión actual responde **304** sin cuerpo.
- `PUT /turnos/{id}/reprogramar` y `DELETE /turnos/{id}` aceptan `If-Match`: si el turno cambió
  desde que el cliente lo leyó, responden **412** y no tocan nada.
- `If-Match` es opcional, así que los clientes que no lo mandan funcionan como antes. Si dos
  transacciones chocan igual, `OptimisticLockException` se traduce a **409**.
- El frontend manda `If-Match` con la versión que está mostrando.

**Idempotency-Key.**
- En `POST /turnos/{id}/confirmar` y `POST /asistente-agendamiento/confirmacion`, la primera
  respuesta exitosa se guarda junto con la clave (tabla `idempotencia`, única por clave y
  usuario, vigente 24 horas).
- Repetir el pedido con la misma clave devuelve esa respuesta, con `Idempotent-Replayed: true`,
  sin volver a ejecutar.
- La misma clave en otra operación responde **422**.
- En `/turnos/{id}/confirmar`, la confirmación y el registro van en **la misma transacción**.
  En el asistente (`@Stateful`, cuyo `confirmar()` es `@Remove` y no puede ir dentro de otra
  transacción) la clave se consulta antes y se guarda después.
- El frontend genera un UUID por reserva y por modo de confirmación.

**Protección XXE.**
- El servicio SOAP del legado tiene un filtro (`ProteccionXxeFilter`) que rechaza, **antes
  del parser**, cualquier mensaje con `<!DOCTYPE` o `<!ENTITY`.
- Responde con un SOAP Fault (`soap:Client`) sin detalles internos y limita el tamaño del
  mensaje a 1 MB.
- El parser de CXF ya rechaza DTD por defecto, pero su Fault exponía detalles del parser y
  la protección dependía de una configuración que nadie controlaba.

**Broker persistente.**
- La instancia de Artemis (journal, suscripciones durables y DLQ) vive en el volumen
  `datos-broker`. Se monta la carpeta entera de la instancia, que en la imagen pertenece al
  usuario `artemis`.

## Alternativas consideradas

- **Reemplazar `{"error"}` por Problem Details sin compatibilidad.** Es más prolijo, pero
  rompe a los clientes existentes en el medio del cuatrimestre.
- **If-Match obligatorio (428 si falta).** Es más estricto, pero obliga a cambiar todos los
  clientes a la vez.
- **Idempotencia en un filtro JAX-RS genérico.** Abarca más endpoints, pero sin poder elegir
  la transacción ni manejar el caso del bean `@Stateful`.
- **Confiar solo en la configuración de CXF para XXE.** Es invisible y fácil de perder.

## Consecuencias

- Cualquier cliente puede interpretar los errores con un estándar. El cambio fue invisible
  para el usuario: los mensajes son los mismos.
- Una pestaña vieja ya no puede reprogramar ni cancelar un turno que otro cambió.
- Un reintento o un doble clic no generan dos confirmaciones ni dos reclamos.
- Hay una tabla más (`idempotencia`) que crece con cada confirmación con clave. Se
  consideran válidos solo los registros de 24 horas; una limpieza periódica queda pendiente.
- `docker compose down` ya no borra los mensajes pendientes del broker; `down -v` sí.

## Cómo se verifica

- **`IntegracionApiTest`** (contra el sistema levantado):
  - INT-01: Problem Details y su `Content-Type`;
  - INT-02: ETag y 304;
  - INT-03: 412 con una versión vieja, sin cambios en el turno;
  - INT-04: clave repetida (misma respuesta, sin doble confirmación) y 422;
  - INT-05: el XXE se rechaza sin exponer el archivo ni detalles del parser.
- **`SeguridadApiTest`:** los errores solo traen los campos de Problem Details.
- **Pruebas unitarias:** `IdempotenciaServiceTest`, `EtagDeTurnoTest` y `ExceptionMappersTest`
  (incluidos 422 y 409 por concurrencia).
- **Frontend:** `baseQuery.test.ts` (If-Match e Idempotency-Key en los pedidos).
- **Prueba manual:** dejar 3 mensajes en una cola de prueba, `docker compose down` y `up`, y
  los 3 siguen ahí.
