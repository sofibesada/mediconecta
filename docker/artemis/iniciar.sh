#!/bin/bash
# Arranque del broker de MediConecta (reemplaza al /docker-run.sh de la imagen).
#
# 1. Crea la instancia la primera vez (igual que la imagen).
# 2. En CADA arranque aplica docker/artemis/broker.xml. La imagen solo lo copia al crear la
#    instancia, y como la instancia vive en un volumen, un cambio de configuracion no se
#    aplicaria nunca.
# 3. En cada arranque regenera las cuentas desde las variables de entorno (SEC-07):
#      ARTEMIS_USER        administrador (consola y healthcheck)       rol amq
#      ARTEMIS_PUB_USER    WildFly, para publicar (solo send)          rol publicador
#      ARTEMIS_CON_USER    WildFly, para los MDB (solo consume)        rol consumidor
#    Las contrasenas se guardan con hash (ENC(...)), nunca en texto plano, y no estan en el repo.
set -euo pipefail

cd /var/lib/artemis-instance
ARTEMIS=/opt/activemq-artemis/bin/artemis

for v in ARTEMIS_USER ARTEMIS_PASSWORD ARTEMIS_PUB_USER ARTEMIS_PUB_PASSWORD ARTEMIS_CON_USER ARTEMIS_CON_PASSWORD; do
  if [ -z "${!v:-}" ]; then
    echo "[mediconecta] falta la variable $v" >&2
    exit 1
  fi
done
if [ "$ARTEMIS_PUB_USER" = "$ARTEMIS_CON_USER" ] || [ "$ARTEMIS_PUB_USER" = "$ARTEMIS_USER" ] || [ "$ARTEMIS_CON_USER" = "$ARTEMIS_USER" ]; then
  echo "[mediconecta] las tres cuentas del broker tienen que ser distintas" >&2
  exit 1
fi

if ! [ -f ./etc/broker.xml ]; then
  "$ARTEMIS" create --user "$ARTEMIS_USER" --password "$ARTEMIS_PASSWORD" --silent --require-login .
fi

cp ./etc-override/broker.xml ./etc/broker.xml

hash() {
  "$ARTEMIS" mask --hash "$1" | sed -n 's/^result: //p'
}

umask 077
{
  echo "# Generado por iniciar.sh en cada arranque. No editar a mano."
  echo "$ARTEMIS_USER = ENC($(hash "$ARTEMIS_PASSWORD"))"
  echo "$ARTEMIS_PUB_USER = ENC($(hash "$ARTEMIS_PUB_PASSWORD"))"
  echo "$ARTEMIS_CON_USER = ENC($(hash "$ARTEMIS_CON_PASSWORD"))"
} > ./etc/artemis-users.properties
{
  echo "# Generado por iniciar.sh en cada arranque. No editar a mano."
  echo "amq = $ARTEMIS_USER"
  echo "publicador = $ARTEMIS_PUB_USER"
  echo "consumidor = $ARTEMIS_CON_USER"
} > ./etc/artemis-roles.properties
# umask no cambia los permisos de un archivo que ya existia (instancia creada antes).
chmod 600 ./etc/artemis-users.properties ./etc/artemis-roles.properties

echo "[mediconecta] broker.xml aplicado; cuentas: $ARTEMIS_USER (amq), $ARTEMIS_PUB_USER (publicador), $ARTEMIS_CON_USER (consumidor)"
exec ./bin/artemis "$@"
