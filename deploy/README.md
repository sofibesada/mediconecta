# Despliegue de MediConecta

Guía para dejar la aplicación corriendo en WildFly + PostgreSQL desde cero.

## 1. Requisitos

- JDK 17
- Maven 3.9+
- WildFly 41.x (`WILDFLY_HOME`)
- PostgreSQL 14+ escuchando en `localhost:5432`

## 2. Base de datos

```sql
CREATE DATABASE mediconecta;
```

Las tablas las crea Hibernate al desplegar (`hibernate.hbm2ddl.auto=update` en
`src/main/resources/META-INF/persistence.xml`).

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

## 5. Usuarios de seguridad (rol admin)

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

## 6. Build y deploy

```bash
mvn clean package wildfly:deploy
```

Credenciales de administración de WildFly usadas por `wildfly-maven-plugin`:
están en `~/.m2/settings.xml` bajo `<server><id>wildfly-admin</id>`, **no** en
el `pom.xml`.

## 7. Verificación rápida

```bash
curl -s -X POST http://localhost:8080/mediconecta/api/usuarios \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Test","email":"test@test.com","password":"1234"}'

curl -s -u admin1:TU_PASS http://localhost:8080/mediconecta/api/usuarios/1
```
