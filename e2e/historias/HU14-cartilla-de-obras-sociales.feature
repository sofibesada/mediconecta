# language: es
@HU-14 @should
Característica: Cartilla de obras sociales de cada profesional
  Como paciente
  quiero saber qué profesionales atienden mi obra social
  para elegir un turno que me cubra o saber de antemano que lo voy a pagar completo

  @E2E-15
  Escenario: El profesional elige qué obras sociales atiende
    Dado que ingresé como profesional
    Cuando en "Mi Perfil" elijo "Solo las que marco" y marco OSDE y PAMI
    Entonces veo "Cartilla actualizada"
    Y los pacientes ven que atiendo OSDE y PAMI

  Escenario: Por defecto un profesional atiende todas
    Dado que el administrador acaba de dar de alta a un profesional
    Cuando un paciente con cualquier obra social lo elige en "Agendar turno"
    Entonces ve que atiende su obra social

  @E2E-14
  Escenario: El profesional no atiende mi obra social
    Dado que tengo OSDE y el profesional solo atiende Swiss Medical
    Cuando lo elijo en "Agendar turno"
    Entonces veo "No atiende OSDE: el turno sería particular"
    Y al reservar, el botón dice "Confirmar como particular"
    Y el turno se confirma como particular, sin consultar a la obra social

  @E2E-14
  Escenario: Busco solo profesionales que atienden mi obra social
    Dado que tengo una obra social cargada
    Cuando marco "Mostrar solo quienes atienden" mi obra social
    Entonces la lista de profesionales no muestra a los que no la atienden

  Escenario: El servidor lo exige aunque se saltee la pantalla
    Dado que el profesional no atiende mi obra social
    Cuando confirmo el turno con mi obra social llamando a la API
    Entonces recibo 409 "Este profesional no atiende tu obra social"
    Y el turno sigue reservado para confirmarlo como particular
