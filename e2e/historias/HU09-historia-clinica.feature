# language: es
@HU-09 @should
Característica: Historia clínica
  Como profesional
  quiero ver y completar la historia clínica de mis pacientes, con su cobertura y los descuentos de cada receta
  para atenderlos con toda su información

  Escenario: Registro un diagnóstico y una receta
    Dado que soy el profesional de un paciente
    Cuando registro un diagnóstico y una receta
    Entonces veo "Diagnóstico registrado" y "Receta registrada"
    Y los dos aparecen en su historia clínica con la fecha de hoy

  Escenario: Veo qué recetas tienen descuento con su obra social
    Dado que el paciente tiene OSDE y le receté Enalapril
    Entonces la receta muestra 40 % de descuento

  Escenario: No veo la historia de pacientes que no atiendo
    Dado que un paciente no tiene turnos conmigo
    Cuando pido su historia clínica
    Entonces la respuesta es 404
