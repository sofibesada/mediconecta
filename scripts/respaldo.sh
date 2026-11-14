#!/usr/bin/env bash
# Respaldo cifrado de la base de MediConecta (SEC-10).
#
#   scripts/respaldo.sh                 crea respaldos/mediconecta-AAAAMMDD-HHMMSS.dump.enc
#
# - pg_dump corre dentro del contenedor "db"; el volcado sale por un pipe y se cifra en esta
#   maquina con AES-256 (openssl, clave derivada con PBKDF2). Nunca toca el disco sin cifrar.
# - Al lado queda un .hmac (HMAC-SHA256 del archivo cifrado): restaurar.sh lo verifica antes
#   de descifrar, asi un respaldo modificado o danado se rechaza.
# - La clave es MEDICONECTA_CLAVE_RESPALDOS (variable de entorno o linea del .env), distinta
#   de la de los datos clinicos. Guardarla FUERA de esta maquina: sin ella no se restaura.
# - Conserva los ultimos MEDICONECTA_RESPALDOS_A_CONSERVAR (7 por defecto) y borra los demas.
#
# Para programarlo: cron (Linux/Mac) o el Programador de tareas de Windows con Git Bash.
set -euo pipefail

cd "$(dirname "$0")/.."

leer_env() {
  # Lee una variable del .env sin ejecutarlo (no se hace "source").
  local nombre="$1"
  if [ -z "${!nombre:-}" ] && [ -f .env ]; then
    local valor
    valor=$( (grep -E "^${nombre}=" .env || true) | tail -1 | cut -d= -f2- | tr -d '\r')
    if [ -n "$valor" ]; then
      export "$nombre=$valor"
    fi
  fi
}

leer_env MEDICONECTA_CLAVE_RESPALDOS
leer_env DB_USER
if [ -z "${MEDICONECTA_CLAVE_RESPALDOS:-}" ] || [ "${#MEDICONECTA_CLAVE_RESPALDOS}" -lt 32 ]; then
  echo "Falta MEDICONECTA_CLAVE_RESPALDOS (minimo 32 caracteres) en el entorno o en .env" >&2
  echo "Generarla con:  openssl rand -base64 32" >&2
  exit 1
fi
command -v openssl >/dev/null || { echo "Hace falta openssl (viene con Git Bash)" >&2; exit 1; }

DESTINO="${MEDICONECTA_RESPALDOS_DIR:-respaldos}"
CONSERVAR="${MEDICONECTA_RESPALDOS_A_CONSERVAR:-7}"
USUARIO="${DB_USER:-mediconecta}"
mkdir -p "$DESTINO"
chmod 700 "$DESTINO" 2>/dev/null || true
umask 077

archivo="$DESTINO/mediconecta-$(date +%Y%m%d-%H%M%S).dump.enc"
temporal="$archivo.parcial"
trap 'rm -f "$temporal"' EXIT

# Formato "custom" de pg_dump: comprimido y restaurable con pg_restore.
docker compose exec -T db pg_dump -U "$USUARIO" -d mediconecta --format=custom --no-owner \
  | openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000 -pass env:MEDICONECTA_CLAVE_RESPALDOS -out "$temporal"

if [ ! -s "$temporal" ]; then
  echo "El respaldo quedo vacio: no se guardo" >&2
  exit 1
fi
mv "$temporal" "$archivo"
openssl dgst -sha256 -hmac "$MEDICONECTA_CLAVE_RESPALDOS" -r "$archivo" | cut -d' ' -f1 > "$archivo.hmac"

# Rotacion: se quedan los ultimos N.
ls -1t "$DESTINO"/mediconecta-*.dump.enc 2>/dev/null | tail -n +"$((CONSERVAR + 1))" | while read -r viejo; do
  rm -f "$viejo" "$viejo.hmac"
done

echo "Respaldo cifrado: $archivo ($(wc -c < "$archivo") bytes)"
