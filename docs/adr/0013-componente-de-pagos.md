# ADR 0013 · Componente de Pagos con una pasarela externa

- **Estado:** aceptada
- **Fecha:** 14/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

Al confirmar un turno, el paciente ve cuánto le toca pagar (el copago si tiene obra social, o
el valor completo si es particular), pero no tenía cómo pagarlo: no sabía si debía llevar la
plata al consultorio. Faltaba el octavo componente del sistema.

Cobrar con tarjeta tiene tres riesgos propios:

- **Cobrar dos veces**: un doble clic o un corte de red hace que el pedido llegue repetido.
- **Datos de tarjeta**: no queremos guardar el número completo ni el código de seguridad.
- **Dependencia externa**: la pasarela puede tardar, caerse o rechazar la tarjeta, y eso no
  tiene que romper el turno.

## Decisión

Sumamos el componente **Pagos** (`ar.com.mediconecta.pagos`) con las mismas capas que el resto
(ver [ADR 0010](0010-capas-repository-y-dto.md)), y una **pasarela simulada** (`pagos-mock`)
desplegada aparte, como las de videollamadas y obras sociales.

| Pieza | Qué hace |
|---|---|
| `PagoResource` (REST) | `POST /pagos` con `Idempotency-Key`; `GET /pagos/turno/{id}` para el paciente, el profesional o el administrador |
| `PagoService` (EJB `@Stateless`) | Reglas: solo el paciente del turno, solo turnos confirmados, con importe y sin pagar |
| `Tarjetas` | Marca por los primeros dígitos (Visa, Mastercard, American Express, Diners, Cabal, Naranja, Maestro), largo, Luhn, vencimiento y código de seguridad |
| `PasarelaPagosClient` (**Adapter**) | Traduce al contrato de la pasarela, con timeouts de 2 s / 5 s; un 422 es un dato inválido y el resto, «pasarela no disponible» (503) |
| `PagoRepository` + entidad `Pago` | Guarda marca, últimos 4 dígitos, código de autorización y motivo de rechazo: **nunca** el número completo ni el código |
| Evento `PAGO_ACREDITADO` | Se publica en el tópico de turnos ([ADR 0005](0005-publish-subscribe-eventos-de-turnos.md)) y avisa al paciente y al profesional |

- **Sin doble cobro, en dos niveles**: la API usa la `IdempotenciaService` del
  [ADR 0012](0012-contrato-http-y-proteccion-xxe.md), y la misma clave viaja a la pasarela,
  que también la respeta. El formulario genera una clave por intento: la conserva si se cortó
  la conexión y la cambia si el servidor ya contestó (por ejemplo, un rechazo).
- **Un rechazo no es un error del sistema**: `PagoRechazadoException` es una
  `@ApplicationException(rollback = false)`, así el intento rechazado queda registrado y la
  API responde **402 Payment Required** con el motivo del banco.
- **Validamos antes de llamar a la pasarela**, en el frontend y en el backend, para dar un
  mensaje claro («El número de tarjeta no es válido») sin gastar una llamada externa.

## Alternativas consideradas

- **Integrar una pasarela real** (Mercado Pago, por ejemplo): necesita cuenta, credenciales y
  un entorno público para recibir notificaciones. Para el TP alcanza con un simulador que
  respete el mismo tipo de contrato (API key, idempotencia, rechazos realistas).
- **Pagar dentro de la confirmación del turno**: ataba el turno a la pasarela; si la pasarela
  se caía, no se podía confirmar. Separados, el turno se confirma igual y se paga después o en
  el consultorio.
- **Guardar la tarjeta para futuros pagos**: obliga a cumplir PCI DSS. Queda fuera de alcance.

## Consecuencias

- El paciente ve en **Mis Citas** cuánto vale la consulta, cuánto cubre su obra social, cuánto
  paga y si ya está pagado; el administrador ve los pagos en **Facturación**.
- Hay un sistema externo más para levantar y configurar (`PAGOS_API_KEY`, `mediconecta.pagos.url`).
- Pruebas: `TarjetasTest` y `PagoServiceTest` (unitarias), `int06`/`int07` contra la API y
  `E2E-12` en el navegador (rechazo y aprobación con otra tarjeta).
