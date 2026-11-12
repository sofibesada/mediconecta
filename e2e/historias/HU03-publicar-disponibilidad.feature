# language: es
@HU-03 @must
Característica: Publicación de horarios disponibles
  Como profesional
  quiero publicar los horarios en los que atiendo, presenciales o virtuales
  para que los pacientes puedan reservarlos

  @E2E-04
  Escenario: Publico un horario y aparece en mi agenda
    Dado que ingresé como profesional
    Cuando publico un horario presencial para el 14/06/2030 a las 10:00
    Entonces veo "Horario publicado"
    Y el horario del 14/06/2030 10:00 aparece en mi agenda con estado "DISPONIBLE"

  Escenario: No se puede publicar un horario en el pasado
    Dado que ingresé como profesional
    Cuando intento publicar un horario para ayer a las 10:00
    Entonces veo "Elegí una fecha y hora futuras"

  Escenario: No se pueden publicar dos horarios a la misma hora
    Dado que ya publiqué un horario el viernes a las 10:00
    Cuando intento publicar otro el viernes a las 10:00
    Entonces veo "Ya tenés un turno publicado en ese horario"
