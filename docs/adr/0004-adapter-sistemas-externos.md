# ADR 0004 · Adapter para los sistemas externos

- **Estado:** aceptada
- **Fecha:** 13/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

MediConecta depende de dos sistemas que no controla:

- **El sistema legado de obras sociales**, que expone un servicio **SOAP**. Lo usamos para
  listar obras sociales, verificar la cobertura, autorizar prestaciones, ver el descuento de
  los medicamentos y registrar reclamos. Sus tipos (`resultadoCobertura`,
  `resultadoAutorizacion`, etc.) y sus nombres no son los de nuestro dominio.
- **La plataforma de videollamadas**, que expone una API **REST** con API key.

Si el negocio usara directamente los tipos de JAX-WS o el cliente HTTP, un cambio en el
contrato externo se propagaría a todo el sistema. Además, cada llamada podría colgar el hilo
si el sistema externo no responde.

## Decisión

Aplicamos el patrón **Adapter**, en dos capas por integración:

| Capa | Obras sociales (SOAP) | Videollamadas (REST) |
|---|---|---|
| Cliente técnico (`integracion`) | `ObrasSocialesClient` + `ObrasSocialesPortType` + `ContratoLegado` | `VideollamadasClient` + `SalaExterna` |
| Adapter de dominio (`business`) | `ObraSocialService` implementa `IObraSocialService` | `TelemedicinaService` implementa `ITelemedicinaService` |

- **El cliente** se ocupa solo de lo técnico. Arma el puerto a partir del WSDL (*contract
  first*), configura los timeouts (2 s de conexión, 5 s de lectura) y traduce cualquier falla
  de red, timeout o SOAP Fault a una única excepción: `ObrasSocialesNoDisponibleException`
  o `VideollamadasNoDisponibleException`.
- **El adapter** traduce los tipos del contrato a records del dominio (`CoberturaVerificada`,
  `ResultadoAutorizacion`, `CoberturaMedicamento`, ...) y es lo único que conoce el resto de
  MediConecta.
- **Idempotencia:** cada autorización manda como `referencia` el id del turno
  (`turno-<id>`). Si el pedido se repite por un reintento o una reentrega JMS, el legado
  devuelve el mismo resultado.
- La URL de cada sistema se configura con una system property de WildFly, así que no está
  en el código.

## Alternativas consideradas

- **Generar el cliente con `wsimport` y usarlo en todo el negocio.** Es más rápido de
  armar, pero acopla el dominio a los tipos generados.
- **Llamar al sistema externo de forma asincrónica siempre.** La verificación de cobertura
  y la autorización necesitan la respuesta para seguir: el usuario espera el resultado. La
  asincronía se usa donde no hace falta responder en el momento (facturación y salas, ver
  ADR 0006).

## Consecuencias

- Si el legado cambia su contrato, se modifican `ContratoLegado` y `ObraSocialService`;
  el negocio y la interfaz no cambian.
- Las pruebas del negocio reemplazan el cliente por un mock: no hace falta el sistema real.
- Hay un tiempo máximo de espera conocido (5 s), de donde sale el mensaje "El sistema de la
  obra social no responde".

## Cómo se verifica

- `ObrasSocialesBusinessTest`: traducción de los tipos del contrato, legado caído (se
  propaga la excepción), referencia idempotente y auditoría de cada intento.
- `TurnoServiceTest`: si la obra social rechaza o no responde, no se confirma nada y queda
  auditado.
- `TelemedicinaServiceTest`: alta y baja de la sala, eventos repetidos que no duplican la
  sala y acceso solo para los participantes.
- Plataforma de videollamadas caída: se simula con `videollamadas.mock.fallar` (sección
  "Simular fallas" del README); el turno queda confirmado y la sala aparece cuando la
  plataforma vuelve a responder, dentro de los reintentos de la cola.
- E2E-06 y E2E-08: el rechazo de la obra social y el afiliado dado de baja, vistos desde la
  interfaz.
