# language: es
@HU-11 @could
Característica: Uso en celular, tablet y escritorio, con accesibilidad y temas
  Como usuario
  quiero usar MediConecta desde el celular o la computadora, con el teclado y en tema claro u oscuro
  para poder sacar turnos desde cualquier dispositivo

  @E2E-09
  Escenario: La aplicación se adapta a celular, tablet y escritorio
    Dado que abro MediConecta con un ancho de 360, 768 o 1280 píxeles
    Entonces el ancho del contenido no supera el ancho de la pantalla (sin desplazamiento horizontal)
    Y el botón "Ingresar" está dentro de la pantalla sin desplazarse

  @E2E-10
  Escenario: Las pantallas no tienen errores de accesibilidad graves
    Cuando reviso con axe el ingreso, el inicio, "Agendar turno", "Mis Citas", "Mi Perfil" y "Mi Agenda"
    Entonces hay 0 problemas de impacto "serious" o "critical" de WCAG 2.1 A y AA
    Y Lighthouse da 90 o más en accesibilidad en la pantalla de ingreso

  @E2E-11
  Escenario: Cambio a tema oscuro y se recuerda
    Cuando elijo el tema oscuro
    Y recargo la página
    Entonces la aplicación sigue en tema oscuro
