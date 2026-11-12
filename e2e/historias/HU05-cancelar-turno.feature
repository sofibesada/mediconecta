# language: es
@HU-05 @must
Característica: Cancelar un turno
  Como paciente
  quiero cancelar un turno que no voy a poder usar
  para liberar el horario, respetando el aviso mínimo de 24 horas

  Escenario: Cancelo con más de 24 horas de anticipación
    Dado que tengo un turno confirmado dentro de 3 días
    Cuando lo cancelo desde "Mis Citas"
    Entonces el turno queda con estado "CANCELADO"
    Y el profesional recibe la notificación "Turno cancelado"

  @E2E-07
  Escenario: No puedo cancelar con menos de 24 horas
    Dado que tengo un turno confirmado dentro de 5 horas
    Cuando intento cancelarlo
    Entonces veo "No se puede cancelar: faltan menos de 24hs para el turno"
    Y el turno sigue con estado "CONFIRMADO"

  Escenario: No puedo cancelar el turno de otro paciente
    Dado que conozco el número de un turno de otro paciente
    Cuando intento cancelarlo
    Entonces la operación responde 404, como si el turno no existiera
