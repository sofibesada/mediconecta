# language: es
@HU-04 @must
Característica: Agendar y confirmar un turno
  Como paciente
  quiero reservar un horario y confirmarlo, con mi obra social o como particular
  para asegurarme la consulta y saber cuánto voy a pagar

  @E2E-13
  Escenario: Busco por especialidad, modalidad y fecha
    Dado que hay profesionales de varias especialidades con horarios presenciales y virtuales
    Cuando filtro por "Dermatología" y "Solo teleconsulta" desde un día
    Entonces solo veo profesionales de Dermatología
    Y solo veo sus horarios de teleconsulta desde ese día

  @E2E-05
  Escenario: Reservo y confirmo un turno como particular
    Dado que un profesional publicó un horario disponible
    Y que ingresé como paciente sin obra social
    Cuando elijo al profesional, reservo el horario y lo confirmo
    Entonces veo "Turno confirmado" con cobertura "Particular" y "A pagar" igual al valor de la consulta ($ 20.000)
    Y el turno aparece en "Mis Citas" con estado "CONFIRMADO"

  Escenario: La reserva temporal vence a los 5 minutos
    Dado que reservé un horario y no lo confirmé
    Cuando pasan 5 minutos desde la reserva
    Entonces el botón "Confirmar turno" queda deshabilitado
    Y otro paciente puede reservar ese horario

  Escenario: Confirmo con mi obra social y veo el copago
    Dado que tengo cargada la obra social OSDE con el afiliado 12345678
    Cuando confirmo un turno reservado
    Entonces veo un código de autorización
    Y "Copago a tu cargo" igual al 30 % del valor de la consulta

  @E2E-06 @falla-externa
  Escenario: La obra social rechaza la autorización y confirmo como particular
    Dado que tengo cargada la obra social SWISS con el afiliado 12345677 que alcanzó el tope
    Y que reservé un horario
    Cuando intento confirmarlo con la obra social
    Entonces veo "No se pudo confirmar con tu obra social"
    Y el turno sigue con estado "RESERVADO TEMPORAL"
    Y al tocar "Confirmar como particular" veo "Turno confirmado" con cobertura "Particular"

  @falla-externa
  Escenario: El sistema de la obra social no responde
    Dado que el sistema de obras sociales tarda más de 5 segundos
    Cuando intento confirmar el turno con la obra social
    Entonces a los 5 segundos como máximo veo "El sistema de SWISS no responde. Probá de nuevo en unos minutos o confirmá como particular."
    Y el turno sigue con estado "RESERVADO TEMPORAL"
    Y la auditoría registra el intento como "SIN_RESPUESTA"

  @falla-externa
  Escenario: El broker de mensajería está caído
    Dado que el broker de mensajería no está disponible
    Cuando intento confirmar el turno
    Entonces a los 5 segundos como máximo veo "El servicio de mensajería no está disponible. No se guardó ningún cambio; probá de nuevo en unos minutos."
    Y el turno sigue con estado "RESERVADO TEMPORAL"
