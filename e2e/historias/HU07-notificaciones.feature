# language: es
@HU-07 @should
Característica: Avisos de turnos
  Como paciente o profesional
  quiero recibir un aviso cuando un turno se confirma, cancela o reprograma, y un recordatorio el día anterior
  para no perderme ninguna consulta

  Escenario: Al confirmar un turno avisa a los dos
    Dado que un paciente confirmó un turno
    Entonces el paciente recibe "Turno confirmado"
    Y el profesional recibe "Nuevo turno"

  Escenario: Recordatorio 24 horas antes
    Dado que tengo un turno confirmado para mañana
    Cuando corre el proceso de recordatorios
    Entonces recibo "Recordatorio de turno" una sola vez

  Escenario: Un evento repetido no duplica avisos
    Dado que el broker entrega dos veces el mismo evento
    Entonces tengo 1 notificación de ese evento, no 2

  @falla-externa
  Escenario: El broker se cae y vuelve
    Dado que el broker estuvo apagado
    Cuando vuelve a estar disponible
    Y confirmo un turno
    Entonces recibo "Turno confirmado" sin que se reinicie la aplicación
