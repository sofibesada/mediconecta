# language: es
@HU-13 @should
Característica: Editar mi perfil y recuperar la contraseña
  Como paciente, profesional o administrador
  quiero mantener mis datos al día y recuperar el acceso si me olvido la contraseña
  para seguir usando MediConecta sin tener que crear otra cuenta

  Escenario: Cambio mi nombre
    Dado que ingresé a MediConecta
    Cuando cambio mi nombre en "Mi Perfil" y guardo
    Entonces veo mi nombre nuevo en la barra superior

  Escenario: Cambiar el email pide la contraseña actual
    Dado que ingresé a MediConecta
    Cuando cambio mi email sin ingresar mi contraseña actual
    Entonces veo "Para cambiar el email ingresá tu contraseña actual"
    Y si el email ya es de otra cuenta veo "Ya existe una cuenta con ese email"

  Escenario: El profesional elige su especialidad
    Dado que ingresé como profesional
    Cuando elijo "Cardiología" en "Mi Perfil"
    Entonces los pacientes me encuentran al filtrar por "Cardiología"

  Escenario: Mi perfil no muestra datos internos
    Dado que ingresé como profesional
    Cuando abro "Mi Perfil"
    Entonces no veo mi número de usuario interno

  Escenario: Pido recuperar la contraseña
    Dado que no me acuerdo de mi contraseña
    Cuando la pido desde "¿Olvidaste tu contraseña?" o desde "Mi Perfil"
    Entonces el administrador recibe el aviso "Pedido de recuperación de contraseña"
    Y en Administración ve que pedí recuperarla y me genera una contraseña temporal

  Escenario: El pedido no revela si una cuenta existe
    Cuando pido recuperar la contraseña de un email que no está registrado
    Entonces veo el mismo mensaje que si existiera
