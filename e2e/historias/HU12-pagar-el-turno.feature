# language: es
@HU-12 @should
Característica: Pagar el turno online
  Como paciente
  quiero ver cuánto me toca pagar por cada turno y pagarlo con tarjeta
  para saber cuánta plata llevar o no tener que pagar en el consultorio

  @E2E-12
  Escenario: Veo el detalle de lo que tengo que pagar
    Dado que tengo un turno confirmado con un copago
    Cuando abro "Ver detalle" en "Mis Citas"
    Entonces veo el valor de la consulta, cuánto cubre mi obra social y cuánto pago yo
    Y la fila del turno dice "A pagar"

  @E2E-12
  Escenario: Pago con una tarjeta aprobada
    Dado que estoy pagando un turno confirmado
    Cuando ingreso una tarjeta válida, por ejemplo una Mastercard
    Entonces la marca se reconoce mientras escribo
    Y veo "Pago aprobado con Mastercard terminada en 4444"
    Y el turno queda "Pagado" y recibo el aviso "Pago acreditado"

  @E2E-12
  Escenario: El banco rechaza la tarjeta
    Dado que estoy pagando un turno confirmado
    Cuando ingreso una tarjeta que el banco rechaza
    Entonces veo "El pago fue rechazado: La tarjeta fue rechazada por el banco emisor"
    Y el turno sigue "A pagar"
    Y puedo intentar con otra tarjeta, que se cobra como un pago nuevo

  Escenario: Una tarjeta mal escrita no llega a la pasarela
    Dado que estoy pagando un turno confirmado
    Cuando ingreso un número con el dígito verificador mal, una fecha vencida o un código incompleto
    Entonces veo qué dato está mal antes de enviar

  Escenario: Un turno pagado no se cobra dos veces
    Dado que ya pagué un turno
    Cuando se repite el pedido de pago (por ejemplo, por un doble clic o un corte de red)
    Entonces se cobra una sola vez

  @falla-externa
  Escenario: La pasarela de pagos está caída
    Dado que la pasarela de pagos no responde
    Cuando intento pagar
    Entonces veo que el servicio de pagos no está disponible
    Y el turno sigue confirmado y "A pagar"
