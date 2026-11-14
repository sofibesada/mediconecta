# Registro de decisiones de arquitectura (ADR)

Cada ADR explica una decisión técnica importante de MediConecta:

- el **contexto**: cuál era el problema;
- la **decisión**: qué elegimos;
- las **alternativas**: qué otras opciones descartamos y por qué;
- las **consecuencias**: qué ganamos y qué costo tiene;
- **cómo se verifica** en las pruebas.

| N.º | Decisión | Patrón / tema |
|---|---|---|
| [0001](0001-patron-state-en-turno.md) | Estados del turno con transiciones inválidas → 409 | State |
| [0002](0002-strategy-politica-de-cancelacion.md) | Política de cancelación de 24 h o urgencia | Strategy |
| [0003](0003-facade-agendamiento.md) | Orquestación del agendamiento | Facade |
| [0004](0004-adapter-sistemas-externos.md) | Obras sociales (SOAP) y videollamadas (REST) | Adapter |
| [0005](0005-publish-subscribe-eventos-de-turnos.md) | Eventos de turnos con suscripciones durables | Publish/subscribe (JMS tópico) |
| [0006](0006-cola-punto-a-punto-facturacion.md) | Reclamos a obras sociales con reintentos y DLQ | Punto a punto (JMS cola) |
| [0007](0007-stateful-y-stateless.md) | Asistente de agendamiento y servicios sin estado | Stateful / Stateless |
| [0008](0008-singleton-y-schedule.md) | Administrador inicial y recordatorios | Singleton + @Schedule |
| [0009](0009-transacciones-declarativas.md) | Confirmación todo o nada (base + broker) | Transacciones declarativas (JTA/XA) |
| [0010](0010-capas-repository-y-dto.md) | Organización de cada componente | Capas, Repository y DTO |
| [0011](0011-frontend-react-redux-toolkit.md) | SPA en React con estado global | React + Redux Toolkit |
| [0012](0012-contrato-http-y-proteccion-xxe.md) | Errores estándar, concurrencia, reintentos seguros y XXE | Problem Details, ETag, Idempotency-Key |
| [0013](0013-componente-de-pagos.md) | Cobro del copago con tarjeta | Componente Pagos + Adapter a una pasarela externa |

Para agregar uno: copiar la estructura de un ADR existente, usar el número siguiente y
sumarlo a esta tabla.
