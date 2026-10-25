# Despliegue de MediConecta

Guía para dejar la aplicación corriendo en WildFly + PostgreSQL desde cero.

## 1. Requisitos

- JDK 17
- Maven 3.9+
- WildFly 41.x (`WILDFLY_HOME`)
- PostgreSQL 14+ escuchando en `localhost:5432`

> **WildFly se arranca SIEMPRE con el perfil full**, que es el que trae
> mensajería (JMS / ActiveMQ Artemis). Con el `standalone.xml` común el deploy
> falla con `default-resource-adapter-name-service not found`.
>
> ```bash
> %WILDFLY_HOME%\bin\standalone.bat -c standalone-full.xml
> ```
>
> Los pasos 3 a 5 (driver, datasource, JMS) se hacen una sola vez, con WildFly
> levantado en ese perfil.

## 2. Base de datos

```sql
CREATE DATABASE mediconecta;
```

Las tablas las crea Hibernate al desplegar (`hibernate.hbm2ddl.auto=update` en
`src/main/resources/META-INF/persistence.xml`). En una base **vacía** se crean
todas con el esquema completo; no hay que correr ningún script.

### Reiniciar la base (esquema desactualizado o datos de prueba)

`hbm2ddl.auto=update` agrega columnas nuevas, pero **no** puede agregar una
columna `NOT NULL` a una tabla que ya tiene filas. Si venís de una versión
anterior, la forma más limpia es borrar las tablas y dejar que Hibernate las
recree en el próximo despliegue:

```sql
DROP TABLE IF EXISTS salas_virtuales, notificaciones, turnos, diagnosticos, recetas, usuarios CASCADE;
```

Después `mvn clean package wildfly:deploy`. Hibernate recrea todo y el bean
`SeedAdmin` vuelve a crear la cuenta ADMIN.

> No cambiar `hbm2ddl.auto` a `create`: eso borraría los datos en **cada**
> redeploy.

### Cuenta ADMIN

Se siembra sola al desplegar (clase `ar.com.mediconecta.config.SeedAdmin`):

- **Email:** `admin@mediconecta.com`
- **Contraseña:** `***REMOVIDO***`

Es la única forma de tener un ADMIN (el registro público solo crea PACIENTE).
Los profesionales los da de alta el ADMIN desde la pantalla de Administración.

## 3. Driver de PostgreSQL en WildFly

Descargar `postgresql-42.7.x.jar` y registrarlo como módulo:

```bash
$WILDFLY_HOME/bin/jboss-cli.bat --connect
module add --name=org.postgresql --resources=/ruta/postgresql-42.7.4.jar --dependencies=jakarta.transaction.api,jakarta.api
/subsystem=datasources/jdbc-driver=postgresql:add(driver-name=postgresql,driver-module-name=org.postgresql,driver-class-name=org.postgresql.Driver)
```

## 4. Datasource `java:/MediconectaDS`

La contraseña **no está versionada**. Setear la variable de entorno antes de
arrancar WildFly:

```bash
set MEDICONECTA_DB_PASSWORD=tu_password        # Windows CMD
$env:MEDICONECTA_DB_PASSWORD="tu_password"     # PowerShell
```

Luego, una de estas dos opciones:

- **Archivo desplegable:** copiar `deploy/mediconecta-ds.xml` a
  `$WILDFLY_HOME/standalone/deployments/`.
- **Script CLI:** `jboss-cli.bat --connect --file=deploy/setup-datasource.cli`

## 5. Mensajería (JMS)

Crea el tópico `TurnosEventosTopic` (JNDI `java:/jms/topic/TurnosEventos`), la cola
`FacturacionQueue` (JNDI `java:/jms/queue/Facturacion`) y la configuración de
reintentos de cada una (3 intentos, 2 s entre cada uno, después a la `DLQ`):

```bash
jboss-cli.bat --connect --file=deploy/setup-jms.cli
```

Al tópico se suscriben dos MDB de MediConecta: `NotificacionTurnoMDB`
(notificaciones) y `TelemedicinaMDB` (salas de videollamada). Las
suscripciones son durables y se crean solas al desplegar.

Además, el timer `RecordatorioTurnos` (`@Schedule`, cada minuto) publica en el mismo
tópico un `TURNO_RECORDATORIO` por cada turno confirmado que empieza en las próximas
24 horas. No requiere configuración.

La cola `FacturacionQueue` (punto a punto) la consume `FacturacionMDB`: cada reclamo a
una obra social lo procesa un único consumidor. Si ya habían corrido el script antes
de la Entrega 4, alcanza con correr solo las dos líneas de la cola de `setup-jms.cli`.

## 6. Seguridad (Jakarta Security con token)

Los usuarios son los de la base de MediConecta (no hace falta `add-user`). Al iniciar
sesión, `POST /api/usuarios/login` devuelve un `token` que hay que mandar en cada
pedido como `Authorization: Bearer <token>`. `JwtAuthenticationMechanism` lo valida y
le informa al contenedor el usuario y su rol (`PACIENTE`, `PROFESIONAL` o `ADMIN`);
`web.xml` y los `@RolesAllowed` de los EJB hacen el resto.

Una sola vez, con WildFly levantado:

```bash
jboss-cli.bat --connect --file=deploy/setup-seguridad.cli
```

Clave de firma de los tokens (recomendado; sin ella se genera una al azar y los
tokens dejan de valer en cada redeploy). Mínimo 32 caracteres, **no versionarla**:

```bash
/system-property=mediconecta.jwt.secret:add(value=UNA_CLAVE_LARGA_Y_SECRETA)
```

## 7. Build y deploy

Son **tres aplicaciones**: MediConecta y dos simuladores de sistemas externos.
- La plataforma de videollamadas (moderna), que MediConecta consume por **REST**.
- El sistema **legado** de obras sociales, que MediConecta consume por **SOAP**.

```bash
cd videollamadas-mock
mvn clean package wildfly:deploy
cd ../obras-sociales-mock
mvn clean package wildfly:deploy
cd ..
mvn clean package wildfly:deploy
```

- MediConecta: `http://localhost:8080/mediconecta/api`
- Videollamadas (mock): `http://localhost:8080/videollamadas-mock/api/v1/rooms`
- Obras sociales (mock, SOAP): WSDL en `http://localhost:8080/obras-sociales-mock/ObrasSociales?wsdl`

Padrón ficticio del simulador de obras sociales (`OSMED`, `PREVENIR`, `SALUDPLUS`;
número de afiliado de 8 dígitos):

| Afiliado | Resultado |
|---|---|
| Termina en **9** | Dado de baja: no se puede cargar la cobertura |
| Termina en **7** | Cobertura válida, pero la autorización de la consulta se **rechaza** |
| Cualquier otro | Cubierto y autorizado (ej. `12345678`) |

Credenciales de administración de WildFly usadas por `wildfly-maven-plugin`:
están en `~/.m2/settings.xml` bajo `<server><id>wildfly-admin</id>`, **no** en
el `pom.xml`.

### Configuración opcional (system properties de WildFly)

| Propiedad | Para qué | Valor por defecto |
|---|---|---|
| `mediconecta.jwt.secret` | Clave HMAC con la que se firman los tokens de sesión | aleatoria en cada deploy |
| `mediconecta.cors.origenes` | Orígenes del frontend que pueden mandar cookies | `http://localhost:5173,http://127.0.0.1:5173` |
| `mediconecta.recordatorio.horas` | Con cuántas horas de anticipación se avisa un turno | `24` |
| `mediconecta.videollamadas.url` | URL base del proveedor de videollamadas | `http://localhost:8080/videollamadas-mock/api/v1` |
| `mediconecta.videollamadas.apiKey` | API key que se le envía al proveedor | `***REMOVIDO***` |
| `videollamadas.mock.fallar` | `true`: el mock responde 503 (para ver los reintentos y la DLQ) | `false` |
| `videollamadas.mock.demoraMs` | El mock tarda N ms (con más de 5000 vence el timeout del cliente) | `0` |
| `mediconecta.obrassociales.url` | Endpoint SOAP del sistema de obras sociales | `http://localhost:8080/obras-sociales-mock/ObrasSociales` |
| `mediconecta.consulta.valor` | Valor de la consulta (base del copago y del reclamo) | `20000` |
| `obrassociales.mock.fallar` | `true`: `registrarReclamo` responde con SOAP Fault (reintentos de la cola y DLQ) | `false` |
| `obrassociales.mock.demoraMs` | El mock tarda N ms (con más de 5000 la autorización no responde y se revierte la confirmación) | `0` |

Se setean sin reiniciar, por ejemplo:
`/system-property=videollamadas.mock.fallar:add(value=true)` en `jboss-cli`
(y `:remove` para volver a la normalidad).

## 8. Frontend

```bash
python -m http.server 5173 --directory frontend
```

Abrir `http://localhost:5173`.

## 9. Verificación rápida

```bash
curl -s -X POST http://localhost:8080/mediconecta/api/usuarios \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Test","email":"test@test.com","password":"1234"}'

# Login como admin (la respuesta trae "token") y consulta protegida con rol ADMIN
curl -s -X POST http://localhost:8080/mediconecta/api/usuarios/login   -H "Content-Type: application/json"   -d '{"email":"admin@mediconecta.com","password":"***REMOVIDO***"}'

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/mediconecta/api/usuarios/1
```
