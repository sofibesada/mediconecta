# language: es
@HU-01 @must
Característica: Registro e ingreso de pacientes
  Como paciente
  quiero crear mi cuenta e ingresar con mi email y contraseña
  para poder sacar turnos sin llamar al consultorio

  @E2E-01
  Escenario: Un paciente nuevo se registra y queda con la sesión iniciada
    Dado que no tengo cuenta en MediConecta
    Cuando me registro con mi nombre, un email nuevo y una contraseña
    Entonces veo el inicio con el saludo "Hola, <mi nombre>"
    Y el menú me ofrece "Agendar turno" y "Mis Citas"

  @E2E-02
  Escenario: Ingreso con una contraseña incorrecta
    Dado que tengo una cuenta de paciente
    Cuando intento ingresar con una contraseña equivocada
    Entonces veo el mensaje "Email o contraseña incorrectos"
    Y sigo en la pantalla de ingreso

  Escenario: No se puede registrar dos veces el mismo email
    Dado que ya existe una cuenta con mi email
    Cuando intento registrarme otra vez con ese email
    Entonces veo el mensaje "El correo ya está registrado"

  Escenario: La sesión vence si no uso la aplicación
    Dado que ingresé y no hice clic ni usé el teclado durante más de 15 minutos
    Cuando intento abrir "Mis Citas"
    Entonces veo "Tu sesión venció. Volvé a iniciar sesión."
    Y vuelvo a la pantalla de ingreso
