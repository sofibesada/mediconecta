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
- **Contraseña:** `MediConecta.Admin.2026`

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

Crea el tópico `TurnosEventosTopic` (JNDI `java:/jms/topic/TurnosEventos`) y su
configuración de reintentos (3 intentos, 2 s entre cada uno, después a la `DLQ`):

```bash
jboss-cli.bat --connect --file=deploy/setup-jms.cli
```

Al tópico se suscriben dos MDB de MediConecta: `NotificacionTurnoMDB`
(notificaciones) y `TelemedicinaMDB` (salas de videollamada). Las
suscripciones son durables y se crean solas al desplegar.

## 6. Usuarios de seguridad (rol admin)

La operación `GET /api/usuarios/{id}` está protegida con rol `admin`
(`web.xml` + `@RolesAllowed`). Crear un usuario de aplicación:

```bash
$WILDFLY_HOME/bin/add-user.bat
# Tipo: b (Application User)
# Realm: ApplicationRealm
# Username: admin1
# Password: (a elección)
# Grupos: admin
```

## 7. Build y deploy

Son **dos aplicaciones**: MediConecta y el simulador de la plataforma externa
de videollamadas (telemedicina), que MediConecta consume por REST.

```bash
cd videollamadas-mock
mvn clean package wildfly:deploy
cd ..
mvn clean package wildfly:deploy
```

- MediConecta: `http://localhost:8080/mediconecta/api`
- Videollamadas (mock): `http://localhost:8080/videollamadas-mock/api/v1/rooms`

Credenciales de administración de WildFly usadas por `wildfly-maven-plugin`:
están en `~/.m2/settings.xml` bajo `<server><id>wildfly-admin</id>`, **no** en
el `pom.xml`.

### Configuración opcional (system properties de WildFly)

| Propiedad | Para qué | Valor por defecto |
|---|---|---|
| `mediconecta.videollamadas.url` | URL base del proveedor de videollamadas | `http://localhost:8080/videollamadas-mock/api/v1` |
| `mediconecta.videollamadas.apiKey` | API key que se le envía al proveedor | `mediconecta-dev-key` |
| `videollamadas.mock.fallar` | `true`: el mock responde 503 (para ver los reintentos y la DLQ) | `false` |
| `videollamadas.mock.demoraMs` | El mock tarda N ms (con más de 5000 vence el timeout del cliente) | `0` |

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

curl -s -u admin1:TU_PASS http://localhost:8080/mediconecta/api/usuarios/1
```
