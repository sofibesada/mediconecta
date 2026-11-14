#!/usr/bin/env bash
# Restaura un respaldo cifrado hecho con scripts/respaldo.sh (SEC-10).
#
#   scripts/restaurar.sh <archivo.dump.enc> --base <nombre> [--si]
#
# - Verifica el HMAC antes de descifrar: un respaldo modificado o danado se rechaza.
# - --base es obligatorio. Si la base existe se REEMPLAZA su contenido, por eso con la base
#   real ("mediconecta") hace falta ademas --si. Para probar un respaldo sin tocar nada:
#     scripts/restaurar.sh respaldos/<archivo> --base prueba_restauracion
#   crea esa base aparte, restaura ahi y se puede borrar despues.
# - Los diagnosticos y recetas siguen cifrados dentro del respaldo: para leerlos, la app
#   necesita la MISMA MEDICONECTA_CLAVE_DATOS que tenia cuando se hizo.
set -euo pipefail

cd "$(dirname "$0")/.."

archivo=""
base=""
confirmado=false
while [ $# -gt 0 ]; do
  case "$1" in
    --base) base="${2:-}"; shift 2 ;;
    --si) confirmado=true; shift ;;
    *) archivo="$1"; shift ;;
  esac
done

if [ -z "$archivo" ] || [ -z "$base" ]; then
  echo "Uso: scripts/restaurar.sh <archivo.dump.enc> --base <nombre> [--si]" >&2
  exit 1
fi
if ! [[ "$base" =~ ^[a-z_][a-z0-9_]{0,62}$ ]]; then
  echo "Nombre de base invalido: solo minusculas, numeros y _" >&2
  exit 1
fi
if [ "$base" = "mediconecta" ] && [ "$confirmado" != true ]; then
  echo "Esto reemplaza los datos de la base real 'mediconecta'. Repetilo con --si si es lo que querés." >&2
  exit 1
fi
[ -f "$archivo" ] || { echo "No existe $archivo" >&2; exit 1; }
[ -f "$archivo.hmac" ] || { echo "Falta $archivo.hmac: no se puede comprobar que el respaldo este intacto" >&2; exit 1; }

leer_env() {
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
[ -n "${MEDICONECTA_CLAVE_RESPALDOS:-}" ] || { echo "Falta MEDICONECTA_CLAVE_RESPALDOS" >&2; exit 1; }
USUARIO="${DB_USER:-mediconecta}"

esperado=$(tr -d ' \r\n' < "$archivo.hmac")
calculado=$(openssl dgst -sha256 -hmac "$MEDICONECTA_CLAVE_RESPALDOS" -r "$archivo" | cut -d' ' -f1)
if [ "$esperado" != "$calculado" ]; then
  echo "El respaldo fue modificado o la clave no es la correcta: no se restaura" >&2
  exit 1
fi

existe=$(docker compose exec -T db psql -U "$USUARIO" -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname = '$base'")
if [ "$existe" != "1" ]; then
  docker compose exec -T db createdb -U "$USUARIO" "$base"
fi

openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:MEDICONECTA_CLAVE_RESPALDOS -in "$archivo" \
  | docker compose exec -T db pg_restore -U "$USUARIO" -d "$base" --clean --if-exists --no-owner --exit-on-error

echo "Restaurado en la base '$base'"
