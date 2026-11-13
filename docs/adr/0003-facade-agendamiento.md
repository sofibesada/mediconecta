# ADR 0003 · Facade para el agendamiento

- **Estado:** aceptada
- **Fecha:** 13/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

Agendar un turno involucra dos componentes de negocio. Primero, **ServicioDeUsuarios**
verifica que el paciente exista y esté activo. Después, **ServicioDeTurnos** retiene el
horario de forma temporal.

Si la capa de presentación (los recursos JAX-RS y el asistente de agendamiento) coordina
esos pasos, tiene que conocer el orden correcto y depender de los dos servicios. Esa misma
lógica se repetiría en cada lugar que agende un turno.

## Decisión

Aplicamos el patrón **Facade**. `AgendaTurnoFacade`, un bean CDI `@ApplicationScoped`,
expone una sola operación:

```java
Turno agendarTurno(Long pacienteId, Long turnoId)
```

Esa operación valida al paciente con `IUsuarioService` y reserva con `ITurnoService`. El
endpoint `POST /turnos/agendar` delega directamente en la fachada.

La fachada es `@ApplicationScoped` porque no guarda estado: alcanza con una instancia para
toda la aplicación. Puede inyectar `TurnoService` sin problemas porque es `@Stateless`, y
cada llamada la atiende cualquier instancia libre del pool.

## Alternativas consideradas

- **Que el recurso REST llame a los dos servicios.** Acopla la presentación al negocio y
  duplica el orden de las validaciones.
- **Que `TurnoService` llame a `UsuarioService`.** Funciona, pero hace que el servicio de
  turnos dependa del de usuarios para un caso que es de orquestación, no de reglas del turno.

## Consecuencias

- La presentación depende de una sola operación: si cambia el orden o se agrega un paso
  (por ejemplo, verificar la cobertura antes de reservar), cambia solo la fachada.
- La fachada no tiene reglas propias: las reglas siguen en los servicios, así que no se
  duplican.
- Es una capa más, aunque con un solo método.

## Cómo se verifica

- `RecordatoriosYAgendaTest.laAgendaSoloReservaParaPacientesActivos`: con un paciente
  inexistente la fachada rechaza y no llama a `reservarTemporalmente`.
- E2E-05: el paciente reserva y confirma un turno desde la interfaz.
