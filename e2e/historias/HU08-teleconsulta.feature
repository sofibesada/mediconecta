# language: es
@HU-08 @should
Característica: Teleconsulta
  Como paciente y profesional
  queremos entrar a una videollamada para los turnos virtuales
  para atendernos sin ir al consultorio

  Escenario: Un turno virtual confirmado tiene su sala
    Dado que confirmé un turno virtual
    Cuando entro a "Mis Citas"
    Entonces veo el botón "Unirse a la videollamada"

  Escenario: El profesional entra como anfitrión
    Dado que soy el profesional del turno virtual
    Cuando me uno a la videollamada
    Entonces se abre el link de anfitrión de la sala, distinto del link del paciente

  Escenario: Nadie más puede ver la sala
    Dado que no participo de un turno virtual
    Cuando pido su sala
    Entonces la respuesta es 404

  @falla-externa
  Escenario: La plataforma de videollamadas no responde
    Dado que la plataforma de videollamadas responde con error
    Cuando se confirma un turno virtual
    Entonces el turno queda con estado "CONFIRMADO"
    Y cuando la plataforma vuelve a responder, dentro de los 3 reintentos de la cola, aparece "Unirse a la videollamada"
