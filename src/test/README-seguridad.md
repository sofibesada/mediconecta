# Pruebas de seguridad (Clase 11)

`SeguridadApiTest` (JUnit 5 + REST Assured) prueba la API **desplegada** en el WildFly
local con usuarios ficticios que crea en cada corrida. Cubre:

| Criterio | Qué prueba |
|---|---|
| SEC-01 | Sin token: 401 sin datos y la operación no se ejecuta |
| SEC-02 | Duración del token (exp − iat ≤ 900 s), token vencido, firma alterada, `alg none` y control positivo |
| SEC-03 | Un PACIENTE llama operaciones exclusivas de ADMIN: 403 sin cambios, más control positivo con ADMIN |
| SEC-04 | El paciente B intenta ver un turno del paciente A: sin datos, código 404, no confirma existencia y registro del rechazo. Con una **sala virtual que existe**: el dueño la ve (200) y otro paciente recibe 404, igual que con un turno inexistente |
| SEC-07 | Lo prueba `docker/artemis/verificar-permisos.sh` en el broker: la cuenta publicadora no puede consumir, la consumidora no puede publicar y ninguna lee la DLQ. `int11` (en `IntegracionApiTest`) comprueba que confirmar un turno sigue generando la notificación |
| SEC-10 | Diagnósticos y recetas se guardan cifrados con AES-256-GCM (`CifradoDatosTest`): en la base no se lee el texto, un dato alterado no se descifra y sin clave no arranca. Respaldos: `scripts/respaldo.sh` los cifra con AES-256 + HMAC y CI los restaura en una base aparte. Ver ADR 0014. **No cubre**: cifrado de disco del servidor |
| SEC-09 | Cada consulta a una historia clínica o ficha deja un registro (quién, qué recurso, de qué paciente, cuándo, resultado) con solo identificadores; un acceso no autorizado sigue bloqueado y queda como `DENEGADO`, nunca como `AUTORIZADO`; solo el admin ve la auditoría |

Si no se pasa `-Dapi.base`, las pruebas se **saltean** (el `mvn package` del deploy no las corre).

## Requisitos

- WildFly levantado con `standalone-full` y `mediconecta.war` desplegado.
- Para la prueba del **token vencido**: la misma clave de firma en WildFly y en la variable
  `MEDICONECTA_JWT_SECRET` (mínimo 32 caracteres; no la versionen). Sin ella, esa prueba
  queda como *skipped*.
- Credenciales del admin sembrado (ver `deploy/README.md`).

## Windows (PowerShell)

```powershell
$env:MEDICONECTA_JWT_SECRET = "<CLAVE_DE_PRUEBA>"
& "$env:WILDFLY_HOME\bin\standalone.bat" -c standalone-full.xml "-Dmediconecta.jwt.secret=$env:MEDICONECTA_JWT_SECRET"
```

En otra terminal, desde la carpeta del proyecto (con la misma variable definida):

```powershell
$env:MEDICONECTA_JWT_SECRET = "<CLAVE_DE_PRUEBA>"
mvn test "-Dapi.base=http://localhost:8080/mediconecta/api" "-Dadmin.email=EMAIL_ADMIN" "-Dadmin.password=PASSWORD_ADMIN" "-Dwildfly.log=$env:WILDFLY_HOME\standalone\log\server.log"
```

## Mac / Linux

```bash
export MEDICONECTA_JWT_SECRET="$(openssl rand -base64 36)"
"$WILDFLY_HOME/bin/standalone.sh" -c standalone-full.xml -Dmediconecta.jwt.secret="$MEDICONECTA_JWT_SECRET"
```

En otra terminal (con la misma variable exportada):

```bash
mvn test -Dapi.base=http://localhost:8080/mediconecta/api -Dadmin.email=EMAIL_ADMIN -Dadmin.password=PASSWORD_ADMIN -Dwildfly.log="$WILDFLY_HOME/standalone/log/server.log"
```

## Resultados

- `target/seguridad/evidencia.md`: una fila por caso con request, esperado y obtenido
  (sin tokens ni contraseñas).
- `target/surefire-reports/`: reporte de JUnit (`.txt` y `.xml`).

Una prueba que falla documenta un **NO CUMPLE**: no se debe "arreglar" la prueba para que pase.
