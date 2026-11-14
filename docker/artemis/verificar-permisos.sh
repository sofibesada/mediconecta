#!/bin/bash
# SEC-07: comprueba en el broker real que cada cuenta de servicio hace SOLO lo suyo.
# Se corre dentro del contenedor del broker (tiene las variables de las cuentas):
#
#   docker compose exec -T broker bash /opt/mediconecta/verificar-permisos.sh
#
# Cada prueba espera que Artemis AUTENTIQUE a la cuenta y le NIEGUE el permiso: si la cuenta
# no autenticara, el error seria otro (AMQ229031) y la prueba falla igual. Un permiso de mas
# hace fallar la prueba (y deja a lo sumo un mensaje de prueba en la DLQ, nunca datos reales).
set -uo pipefail

CLI=/var/lib/artemis-instance/bin/artemis
URL=tcp://localhost:61616
fallas=0

esperar_negado() {
  local descripcion="$1"; shift
  local salida
  salida=$(timeout 60 "$CLI" "$@" --url "$URL" --silent 2>&1)
  if grep -q "does not have permission" <<<"$salida"; then
    echo "OK    $descripcion"
  else
    echo "FALLA $descripcion"
    grep -E "AMQ[0-9]+|Exception|Consumed|Produced" <<<"$salida" | head -3 | sed 's/^/        /'
    fallas=$((fallas + 1))
  fi
}

esperar_sin_login() {
  local descripcion="$1"; shift
  local salida
  salida=$(timeout 60 "$CLI" "$@" --url "$URL" --silent 2>&1)
  if grep -qE "AMQ229031|Unable to validate user" <<<"$salida"; then
    echo "OK    $descripcion"
  else
    echo "FALLA $descripcion"
    fallas=$((fallas + 1))
  fi
}

PUB=(--user "$ARTEMIS_PUB_USER" --password "$ARTEMIS_PUB_PASSWORD")
CON=(--user "$ARTEMIS_CON_USER" --password "$ARTEMIS_CON_PASSWORD")
CONSUMIR=(--message-count 1 --receive-timeout 2000 --break-on-null)
ENVIAR=(--message-count 1 --message "sec07-prueba-de-permisos")

echo "SEC-07: permisos de las cuentas de servicio del broker"
esperar_negado "la publicadora no puede consumir la cola de facturacion" consumer "${PUB[@]}" "${CONSUMIR[@]}" --destination queue://FacturacionQueue
esperar_negado "la publicadora no puede suscribirse al topico de turnos" consumer "${PUB[@]}" "${CONSUMIR[@]}" --destination topic://TurnosEventosTopic
esperar_negado "la publicadora no puede leer la DLQ" consumer "${PUB[@]}" "${CONSUMIR[@]}" --destination queue://DLQ
esperar_negado "la consumidora no puede publicar en la cola de facturacion" producer "${CON[@]}" "${ENVIAR[@]}" --destination queue://FacturacionQueue
esperar_negado "la consumidora no puede publicar en el topico de turnos" producer "${CON[@]}" "${ENVIAR[@]}" --destination topic://TurnosEventosTopic
esperar_negado "la consumidora no puede leer la DLQ" consumer "${CON[@]}" "${CONSUMIR[@]}" --destination queue://DLQ
esperar_negado "la consumidora no puede crear destinos nuevos" producer "${CON[@]}" "${ENVIAR[@]}" --destination queue://sec07.destino.nuevo
esperar_sin_login "una contrasena incorrecta no entra" producer --user "$ARTEMIS_PUB_USER" --password "incorrecta" "${ENVIAR[@]}" --destination queue://FacturacionQueue

if [ "$fallas" -gt 0 ]; then
  echo "$fallas prueba(s) de permisos fallaron"
  exit 1
fi
echo "Todas las pruebas de permisos pasaron"
