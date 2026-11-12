# language: es
@HU-02 @must
Característica: Alta de profesionales por el administrador
  Como administrador
  quiero dar de alta a los profesionales con una contraseña temporal
  para que solo atiendan en la plataforma profesionales verificados

  @E2E-03
  Escenario: El profesional dado de alta tiene que cambiar la contraseña temporal
    Dado que el administrador creó al profesional con una contraseña temporal
    Cuando el profesional ingresa con la contraseña temporal
    Entonces la aplicación le pide elegir una contraseña nueva antes de continuar
    Y después de cambiarla ve "Mi Agenda" en el menú

  Escenario: Un paciente no puede crear profesionales
    Dado que ingresé como paciente
    Cuando intento usar la función de alta de profesionales
    Entonces la operación se rechaza con 403

  Escenario: Un profesional desactivado no puede ingresar
    Dado que el administrador desactivó a un profesional
    Cuando el profesional intenta ingresar
    Entonces ve "Tu cuenta está desactivada. Contactá al administrador."
