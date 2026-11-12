# language: es
@HU-10 @could
Característica: Reprogramar un turno
  Como profesional
  quiero mover un turno a otro horario
  para resolver imprevistos sin cancelar al paciente

  Escenario: Reprogramo y el paciente recibe aviso con la fecha anterior y la nueva
    Dado que tengo un turno confirmado
    Cuando lo paso a otro horario libre
    Entonces el paciente recibe "Turno reprogramado" con la fecha anterior y la nueva

  Escenario: No puedo reprogramar a un horario ocupado
    Dado que tengo un turno confirmado
    Cuando elijo un horario donde ya tengo otro turno
    Entonces veo "Ya tenés otro turno en ese horario"

  Escenario: No puedo reprogramar un turno que el paciente está confirmando
    Dado que el paciente tiene el turno reservado temporalmente
    Cuando intento reprogramarlo
    Entonces veo "El paciente está confirmando este turno; esperá a que lo confirme o se libere"
