# language: es
@HU-06 @should
Característica: Cargar la cobertura de la obra social
  Como paciente
  quiero cargar mi obra social y número de afiliado
  para que me autoricen las consultas y pagar solo el copago

  @E2E-08
  Escenario: Cargo una cobertura activa
    Dado que ingresé como paciente
    Cuando cargo OSDE con el afiliado 12345678 en "Mi Perfil"
    Entonces veo "Cobertura verificada"
    Y mi perfil muestra el afiliado 12345678 con su plan y el porcentaje de copago

  @E2E-08
  Escenario: Un afiliado dado de baja no se puede cargar
    Dado que ingresé como paciente
    Cuando cargo OSDE con el afiliado 12345679
    Entonces veo "La obra social informó: Afiliado dado de baja"
    Y mi perfil sigue diciendo "No cargaste obra social"

  Escenario: Quito la cobertura y paso a ser particular
    Dado que tengo una obra social cargada
    Cuando la quito
    Entonces mi perfil dice "No cargaste obra social"
    Y el próximo turno que confirmo muestra cobertura "Particular"

  @falla-externa
  Escenario: El sistema de obras sociales está caído
    Dado que el sistema de obras sociales no responde
    Cuando intento cargar mi cobertura
    Entonces veo "El sistema de la obra social no responde (consultarCobertura)"
    Y mi perfil no cambia
