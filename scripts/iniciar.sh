#!/usr/bin/env bash
# Levanta MediConecta completo con un solo comando (G1).
#
#   scripts/iniciar.sh
#
# 1. Si no hay .env, lo crea a partir de .env.example.
# 2. Completa con valores al azar las claves obligatorias que esten vacias:
#    ARTEMIS_PUB_PASSWORD, ARTEMIS_CON_PASSWORD (SEC-07), MEDICONECTA_CLAVE_DATOS y
#    MEDICONECTA_CLAVE_RESPALDOS (SEC-10). Nunca cambia un valor que ya tiene: si se
#    cambiara la clave de datos, los diagnosticos y recetas guardados no se podrian leer.
#    Los valores no se muestran por pantalla.
# 3. docker compose up --build y espera a que la app responda (health check de WildFly).
#
# Se puede correr todas las veces que haga falta: la segunda vez solo levanta lo que falte.
# Requisito: Docker con Docker Compose. En Windows, correrlo desde Git Bash
# (o usar scripts/iniciar.ps1 desde PowerShell).
set -euo pipefail

cd "$(dirname "$0")/.."

clave_base64() {
  # 32 bytes al azar en Base64 (AES-256), igual que "openssl rand -base64 32".
  head -c 32 /dev/urandom | base64 | tr -d '\n'
}

contrasena() {
  # 32 caracteres alfanumericos al azar (sin simbolos, para no romper el .env ni la URL).
  local v
  v=$(head -c 64 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')
  printf '%s' "${v:0:32}"
}

valor_en_env() {
  (grep -E "^$1=" .env || true) | tail -1 | cut -d= -f2- | tr -d '\r'
}

fijar_en_env() {
  # Reemplaza "NOMBRE=" (vacio) por "NOMBRE=valor", o lo agrega al final si no esta.
  local nombre="$1" valor="$2"
  if grep -qE "^${nombre}=" .env; then
    NOMBRE="$nombre" VALOR="$valor" awk '
      { linea = $0; sub(/\r$/, "", linea) }
      linea == ENVIRON["NOMBRE"] "=" { print ENVIRON["NOMBRE"] "=" ENVIRON["VALOR"]; next }
      { print }
    ' .env > .env.tmp
    mv .env.tmp .env
  else
    printf '%s=%s\n' "$nombre" "$valor" >> .env
  fi
}

if ! command -v docker > /dev/null 2>&1; then
  echo "No se encontro Docker. Instalalo (Docker Desktop en Windows/Mac) y volve a correr el script." >&2
  exit 1
fi
if ! docker info > /dev/null 2>&1; then
  echo "Docker esta instalado pero no responde. Abri Docker Desktop (o inicia el servicio) y volve a intentar." >&2
  exit 1
fi

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Se creo .env a partir de .env.example."
fi

generadas=()
for nombre in ARTEMIS_PUB_PASSWORD ARTEMIS_CON_PASSWORD; do
  if [ -z "$(valor_en_env "$nombre")" ]; then
    fijar_en_env "$nombre" "$(contrasena)"
    generadas+=("$nombre")
  fi
done
for nombre in MEDICONECTA_CLAVE_DATOS MEDICONECTA_CLAVE_RESPALDOS; do
  if [ -z "$(valor_en_env "$nombre")" ]; then
    fijar_en_env "$nombre" "$(clave_base64)"
    generadas+=("$nombre")
  fi
done

if [ ${#generadas[@]} -gt 0 ]; then
  echo "Se generaron en .env: ${generadas[*]}"
  case " ${generadas[*]} " in
    *" MEDICONECTA_CLAVE_DATOS "*|*" MEDICONECTA_CLAVE_RESPALDOS "*)
      echo "  Guarda una copia de MEDICONECTA_CLAVE_DATOS y MEDICONECTA_CLAVE_RESPALDOS fuera de"
      echo "  esta maquina: sin ellas no se leen los datos clinicos ni se restauran los respaldos."
      ;;
  esac
else
  echo "El .env ya tiene todas las claves obligatorias; no se cambio nada."
fi

echo
echo "Levantando los contenedores (la primera vez compila y descarga imagenes, unos 5 minutos)..."
if ! docker compose up --build --detach --wait --wait-timeout 900; then
  echo >&2
  echo "Algun servicio no quedo listo. Estado actual:" >&2
  docker compose ps >&2 || true
  echo "Para ver el motivo:  docker compose logs app --tail 100" >&2
  exit 1
fi

cat <<'FIN'

MediConecta esta listo.

  Aplicacion                https://localhost:8443  (certificado de prueba: el navegador avisa la primera vez)
  API REST                  https://localhost:8443/mediconecta/api
  Consola del broker        http://localhost:8161/console
  Panel del legado (SOAP)   http://localhost:8080/obras-sociales-mock/admin.html

  Administrador: admin@mediconecta.com (clave en el README, seccion "Credenciales").
  Los pacientes se registran desde la aplicacion.

Para apagar:  docker compose down      (con -v tambien se borra la base)
FIN
