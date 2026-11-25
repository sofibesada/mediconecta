# ADR 0015 · Orientador de especialidad detrás de un gateway de IA

- **Estado:** aceptada
- **Fecha:** 22/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

El checkpoint del 30/11 pide una funcionalidad de IA que funcione a través de un gateway. Elegimos
un **orientador de especialidad**: el paciente cuenta qué le pasa y la app le sugiere con qué
especialidad sacar turno. Resuelve un problema real: muchos pacientes no saben si un síntoma lo
atiende un clínico, un dermatólogo o un traumatólogo.

Un modelo de lenguaje es un sistema externo poco confiable:
- puede tardar o no responder;
- puede inventar una especialidad que no existe en la app;
- puede seguir instrucciones escondidas en el texto del paciente (prompt injection);
- puede repetir sus propias instrucciones (fuga);
- recibe lo que escribe el paciente, que puede incluir datos personales;
- cada consulta cuesta dinero.

## Decisión

Toda consulta pasa por **`GatewayIA`** (patrón Gateway). Ningún otro código llama al modelo.

| Pieza | Qué hace |
|---|---|
| `AsistenteSalud` (frontend) | Botón flotante «¿Qué te pasa?» con una charla, en todas las pantallas del paciente. Cada mensaje es una consulta independiente; «Sacar turno de…» abre Agendar con la especialidad filtrada |
| `OrientacionResource` | `POST /ia/orientacion {"sintomas": "..."}` |
| `OrientadorService` | `@Singleton`, solo PACIENTE. Arma el gateway según la configuración |
| `GatewayIA` | Controles antes y después de consultar al proveedor, corte a los 5 s y respaldo |
| `ProveedorIA` | Interfaz del modelo. El gateway no sabe cuál hay detrás |
| `ProveedorSimulado` | Por defecto. Palabras clave, sin red ni costo. Permite simular una falla (`lento` / `caido`) |
| `ProveedorClaude` | Claude (Anthropic) con el SDK oficial `anthropic-java`, modelo `claude-opus-5-5`, esfuerzo bajo y sin reintentos |
| `LimiteDeConsultas` | 5 consultas por minuto por paciente → **429** con `Retry-After` |

**Antes de consultar:**
1. Valida el texto: no vacío y hasta 500 caracteres (400 si no se cumple).
2. Controla el límite de consultas.
3. **Urgencias por regla fija, sin IA:** ante dolor de pecho, falta de aire, desmayo o riesgo de
   autolesión indica el 107 o el 135 y la guardia. No depende de que el modelo acierte.
4. **Prompt injection directa:** si el texto intenta cambiar las instrucciones («ignorá las
   instrucciones», «actuá como», «system prompt», `</consulta>`), no se manda al proveedor.
5. **Datos personales:** oculta correos, DNI, teléfonos y números de tarjeta (`[dato oculto]`).
6. El texto del paciente va entre `<consulta>` y `</consulta>`, y las instrucciones dicen que
   es un dato, no una orden.

**Después de consultar** (la respuesta de un modelo no es confiable, por eso se cubre también la
inyección **indirecta**):
7. **Canario:** cada consulta lleva un código al azar en las instrucciones. Si aparece en la
   respuesta, hubo una fuga y se descarta.
8. La especialidad tiene que estar en `Especialidades.LISTA`; si no, se usa el respaldo.
9. El motivo se muestra como texto plano: sin etiquetas HTML, sin enlaces y con 280
   caracteres como máximo.

**Resiliencia:** el proveedor corre en un hilo administrado por WildFly
(`DefaultManagedExecutorService`) y el gateway espera **5 s como máximo**. Si tarda, falla o el
modelo rechaza la consulta (`stop_reason: refusal`), el paciente recibe el **respaldo**:
Clínica médica, marcada como «Sugerencia general». La pantalla nunca queda sin respuesta.

**Configuración** (variable de entorno o system property de WildFly):

| Variable | Valor por defecto |
|---|---|
| `MEDICONECTA_IA_PROVEEDOR` | `simulado` (`claude` para usar Claude) |
| `ANTHROPIC_API_KEY` | vacía. Va solo en el `.env`, nunca en el repositorio. Sin clave se usa el simulado |
| `MEDICONECTA_IA_MODELO` | `claude-opus-5-5` |
| `MEDICONECTA_IA_CONSULTAS_POR_MINUTO` | `5` |
| `mediconecta.ia.simularFalla` | sin valor; `lento` o `caido` para la demo, se cambia en vivo con jboss-cli |

## Alternativas consideradas

- **Charla con memoria (el modelo repregunta).** Para el 30/11 se descartó: habría que mandar el
  historial al modelo y controlar la inyección en cada turno. El asistente tiene forma de charla,
  pero cada mensaje es una consulta independiente al mismo gateway. Queda como mejora posible.

- **Llamar al modelo desde el frontend.** Descartada: la API key quedaría en el navegador y no
  habría límite ni controles.
- **Confiar en las instrucciones del modelo para las urgencias.** Descartada: una urgencia no
  puede depender de que el modelo responda a tiempo o acierte.
- **Solo palabras clave, sin modelo.** Es el proveedor simulado. Sirve para la demo y las pruebas,
  pero no entiende frases que no tiene cargadas. El gateway permite cambiarlo por Claude sin
  tocar el resto del código.
- **Reintentar si el modelo falla.** Descartada: con 5 s de límite no hay tiempo, y el respaldo
  ya le da una respuesta útil al paciente.

## Consecuencias

- La app y todas las pruebas funcionan sin internet ni API key.
- Cambiar de proveedor es implementar `ProveedorIA` y agregarlo a `OrientadorService`.
- El límite vive en memoria: alcanza con un solo WildFly. Con varias instancias habría que
  llevarlo a la base o a una caché compartida.
- La lista de signos de urgencia es fija: hay que revisarla con un profesional de la salud antes
  de usar el orientador con pacientes reales.
- El SDK trae Jackson en `WEB-INF/lib`. Al detectarlo, WildFly empezaba a usar Jackson en lugar
  de JSON-B en toda la API, y se rompían las fechas, el ETag y la idempotencia (lo detectaron
  int03 e int04). En `web.xml` dejamos `resteasy.preferJacksonOverJsonB=false`.

## Cómo se verifica

| Prueba | Qué cubre |
|---|---|
| `GatewayIATest` (43 casos) | Casos con su especialidad esperada, fuera de tema, urgencias, inyección directa e indirecta, canario, datos personales, proveedor caído, lento o que devuelve null, límite y validaciones |
| `OrientadorServiceTest` | Elección del proveedor según la configuración (también Claude sin clave), límite configurable |
| `ProveedorSimuladoTest` | Formato de la respuesta, palabras completas, modos `lento` y `caido` |
| `ExceptionMappersTest` | 429 en Problem Details con `Retry-After` |
| `int12`, `int12b` (API) | 200, urgencia, inyección, 400, 403 para un profesional y 429 con `Retry-After` |
| `AsistenteSalud.test.tsx` | Abrir y cerrar con foco y Escape, sugerencia, «Sacar turno de…» navega con la especialidad, urgencia sin turno, 429 como respuesta, últimos 20 mensajes |
| E2E-18 | Desde el inicio: sugerencia en el asistente, «Sacar turno de…» filtra Agendar, la charla se conserva y una urgencia indica la guardia |

Prueba manual de resiliencia (hecha el 22/11 en Docker): con `simularFalla=lento` la respuesta
llegó en 5,04 s con el respaldo. Con `caido` llegó al instante con el respaldo. Con Claude y una
clave inválida, el SDK cargó dentro de WildFly y respondió el respaldo en 2 s.
