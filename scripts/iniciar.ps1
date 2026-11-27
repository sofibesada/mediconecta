# Levanta MediConecta completo con un solo comando (G1), desde PowerShell en Windows.
#
#   powershell -ExecutionPolicy Bypass -File scripts\iniciar.ps1
#
# Hace lo mismo que scripts/iniciar.sh:
# 1. Si no hay .env, lo crea a partir de .env.example.
# 2. Completa con valores al azar las claves obligatorias que esten vacias
#    (ARTEMIS_PUB_PASSWORD, ARTEMIS_CON_PASSWORD, MEDICONECTA_CLAVE_DATOS,
#    MEDICONECTA_CLAVE_RESPALDOS). Nunca cambia un valor que ya tiene y no los muestra.
# 3. docker compose up --build y espera a que la app responda.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

function New-BytesAlAzar([int]$cantidad) {
    $bytes = New-Object byte[] $cantidad
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    return $bytes
}

function New-ClaveBase64 { [Convert]::ToBase64String((New-BytesAlAzar 32)) }

function New-Contrasena {
    $v = [Convert]::ToBase64String((New-BytesAlAzar 64)) -replace '[^A-Za-z0-9]', ''
    return $v.Substring(0, 32)
}

# El .env se escribe en UTF-8 sin BOM y con LF: docker compose no entiende el BOM.
$utf8SinBom = New-Object System.Text.UTF8Encoding($false)
$rutaEnv = Join-Path (Get-Location) '.env'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host 'No se encontro Docker. Instala Docker Desktop y volve a correr el script.' -ForegroundColor Red
    exit 1
}
# En Windows PowerShell 5.1, redirigir el stderr de un programa con 'Stop' corta el script.
$ErrorActionPreference = 'Continue'
& docker info *> $null
$dockerResponde = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = 'Stop'
if (-not $dockerResponde) {
    Write-Host 'Docker esta instalado pero no responde. Abri Docker Desktop y volve a intentar.' -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $rutaEnv)) {
    Copy-Item '.env.example' $rutaEnv
    Write-Host 'Se creo .env a partir de .env.example.'
}

$lineas = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($rutaEnv)) { $lineas.Add($l.TrimEnd("`r")) }

$obligatorias = [ordered]@{
    'ARTEMIS_PUB_PASSWORD'        = { New-Contrasena }
    'ARTEMIS_CON_PASSWORD'        = { New-Contrasena }
    'MEDICONECTA_CLAVE_DATOS'     = { New-ClaveBase64 }
    'MEDICONECTA_CLAVE_RESPALDOS' = { New-ClaveBase64 }
}
$generadas = @()
foreach ($nombre in $obligatorias.Keys) {
    $indice = -1
    for ($i = 0; $i -lt $lineas.Count; $i++) {
        if ($lineas[$i].StartsWith("$nombre=")) { $indice = $i }
    }
    if ($indice -ge 0 -and $lineas[$indice].Length -gt ($nombre.Length + 1)) { continue }
    $valor = & $obligatorias[$nombre]
    if ($indice -ge 0) { $lineas[$indice] = "$nombre=$valor" } else { $lineas.Add("$nombre=$valor") }
    $generadas += $nombre
}

if ($generadas.Count -gt 0) {
    [System.IO.File]::WriteAllText($rutaEnv, (($lineas -join "`n") + "`n"), $utf8SinBom)
    Write-Host ("Se generaron en .env: " + ($generadas -join ' '))
    if ($generadas -contains 'MEDICONECTA_CLAVE_DATOS' -or $generadas -contains 'MEDICONECTA_CLAVE_RESPALDOS') {
        Write-Host '  Guarda una copia de MEDICONECTA_CLAVE_DATOS y MEDICONECTA_CLAVE_RESPALDOS fuera de'
        Write-Host '  esta maquina: sin ellas no se leen los datos clinicos ni se restauran los respaldos.'
    }
} else {
    Write-Host 'El .env ya tiene todas las claves obligatorias; no se cambio nada.'
}

Write-Host ''
Write-Host 'Levantando los contenedores (la primera vez compila y descarga imagenes, unos 5 minutos)...'
$ErrorActionPreference = 'Continue'   # docker compose informa el progreso por stderr
& docker compose up --build --detach --wait --wait-timeout 900
if ($LASTEXITCODE -ne 0) {
    Write-Host ''
    Write-Host 'Algun servicio no quedo listo. Estado actual:' -ForegroundColor Red
    & docker compose ps
    Write-Host 'Para ver el motivo:  docker compose logs app --tail 100'
    exit 1
}

Write-Host @'

MediConecta esta listo.

  Aplicacion                https://localhost:8443  (certificado de prueba: el navegador avisa la primera vez)
  API REST                  https://localhost:8443/mediconecta/api
  Consola del broker        http://localhost:8161/console
  Panel del legado (SOAP)   http://localhost:8080/obras-sociales-mock/admin.html

  Administrador: admin@mediconecta.com (clave en el README, seccion "Credenciales").
  Los pacientes se registran desde la aplicacion.

Para apagar:  docker compose down      (con -v tambien se borra la base)
'@
