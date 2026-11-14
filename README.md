# MediConecta

Plataforma que conecta pacientes con profesionales de la salud: turnos presenciales y
teleconsultas, cobertura de obras sociales, avisos y facturación.
Grupo 4 · Desarrollo de Aplicaciones II · UADE · 2.º cuatrimestre 2026.

## Levantar todo el sistema (un solo comando)

Requisito: **Docker** con Docker Compose (Docker Desktop en Windows/Mac). Nada más:
Java, Maven, WildFly, PostgreSQL y el broker corren dentro de los contenedores.

La primera vez, crear el archivo `.env` con las contraseñas de las cuentas de servicio del
broker (SEC-07) y la clave que cifra los datos clínicos (SEC-10). No tienen valor por
defecto y el `.env` no se sube al repositorio:

```bash
cp .env.example .env
# completar ARTEMIS_PUB_PASSWORD y ARTEMIS_CON_PASSWORD con dos valores largos y distintos,
# y MEDICONECTA_CLAVE_DATOS con:  openssl rand -base64 32
```

Guarden bien `MEDICONECTA_CLAVE_DATOS`: si se pierde, los diagnósticos y recetas guardados
no se pueden volver a leer.

```bash
docker compose up --build
```

La primera vez compila y descarga las imágenes (unos 5 minutos según la conexión).
El sistema está listo cuando `docker compose ps` muestra el servicio `app` como `healthy`.

| Qué | Dónde |
|---|---|
| Aplicación (frontend) | http://localhost:5173 |
| API REST | http://localhost:8080/mediconecta/api |
| Sistema legado de obras sociales (SOAP) | http://localhost:8080/obras-sociales-mock/ObrasSociales?wsdl |
| Panel del legado (obras sociales, afiliados, vademécum) | http://localhost:8080/obras-sociales-mock/admin.html |
| Plataforma de videollamadas (REST, simulada) | http://localhost:8080/videollamadas-mock/api/v1/rooms |
| Pasarela de pagos con tarjeta (REST, simulada) | http://localhost:8080/pagos-mock/api/v1/pagos |
| Consola del broker ActiveMQ Artemis (colas, DLQ, reproceso) | http://localhost:8161/console |

Para apagar: `docker compose down` (agregar `-v` para borrar también la base de datos).

### Servicios

| Servicio | Imagen | Rol |
|---|---|---|
| `db` | PostgreSQL 16 | Base de datos |
| `broker` | ActiveMQ Artemis 2.39 | Tópico `TurnosEventosTopic`, cola `FacturacionQueue` y `DLQ` |
| `app` | WildFly 41 (perfil full) | MediConecta + los simuladores de videollamadas (REST), pasarela de pagos (REST) y obras sociales (SOAP) |
| `frontend` | nginx 1.27 (build con Node 22) | La SPA en React (ver más abajo) |

### Credenciales del entorno local de demo

Son valores **solo para este entorno local** y están en `docker-compose.yml`. Para
cambiarlos sin tocar el repo, copiar `.env.example` como `.env` y completarlo.

| Uso | Usuario | Clave |
|---|---|---|
| Administrador de MediConecta | `admin@mediconecta.com` | `admin1234` |
| Panel del legado de obras sociales | — | `padron-demo` |
| API key de la pasarela de pagos | — | `pagos-demo` |
| Consola de Artemis | `artemis` | `artemis` |
| Cuenta de WildFly para publicar en el broker | `mediconecta-publicador` | la de `ARTEMIS_PUB_PASSWORD` en `.env` |
| Cuenta de WildFly para consumir del broker | `mediconecta-consumidor` | la de `ARTEMIS_CON_PASSWORD` en `.env` |

Los pacientes se registran desde la aplicación; los profesionales los crea el administrador.

### Datos de prueba del sistema de obras sociales

| Obra social / prepaga | Afiliado | Plan | Resultado |
|---|---|---|---|
| OSDE | `12345678` | OSDE 210 (copago 30 %) | Cubierto y autorizado |
| OSDE | `61234567` | OSDE 410 (copago 10 %) | Cubierto y autorizado |
| OSDE | `12345679` | — | **Dado de baja**: no se puede cargar la cobertura |
| Swiss Medical | `12345677` | SMG30 | **Tope alcanzado**: la autorización se rechaza |
| Swiss Medical | `22223333` | SMG40 (copago 15 %) | Cubierto y autorizado |
| Galeno | `33334444` | Galeno Plata 330 | Cubierto y autorizado |
| Medifé | `44445555` | Medifé Plata | Cubierto y autorizado |
| PAMI | `15012345678900` | PAMI (sin copago) | Cubierto y autorizado |
| IOMA | `66667777` | IOMA | Cubierto y autorizado |
| OSECAC | `77778888` | OSECAC | Cubierto y autorizado |
| SanCor Salud | `88889999` | SanCor 3000 | Cubierto y autorizado |
| OMINT | `99990000` | OMINT Clásico | Cubierto y autorizado |
| OSDEPYM | `11112222` | OSDEPYM 2000 | Cubierto y autorizado |

Son **obras sociales y prepagas reales** de Argentina con los nombres comerciales de sus
planes. Los afiliados, los porcentajes de copago y los descuentos son de ejemplo: el padrón
real de cada entidad no es público, así que lo simula el sistema legado (se administra desde
su panel, ver abajo).

### Tarjetas de prueba de la pasarela de pagos

La pasarela es simulada: no se cobra plata real. Valida la marca (por los primeros dígitos),
el largo, el dígito verificador (Luhn), el vencimiento `MM/AA` y el código de seguridad
(3 dígitos, 4 en American Express). Sirve cualquier vencimiento futuro.

| Tarjeta | Número | Resultado |
|---|---|---|
| Visa | `4111 1111 1111 1111` | Aprobada |
| Mastercard | `5555 5555 5555 4444` | Aprobada |
| American Express | `3782 822463 10005` | Aprobada (código de 4 dígitos) |
| Cabal | `6042 0100 0000 0006` | Aprobada |
| Naranja | `5895 6200 0000 0002` | Aprobada |
| Visa | `4000 0000 0000 0002` | **Rechazada** por el banco emisor |
| Visa | `4000 0000 0000 9995` | **Rechazada**: fondos insuficientes |

El paciente paga desde **Mis Citas → Ver detalle → Pagar online**. Cada pago lleva una
`Idempotency-Key`: si el pedido se repite, se cobra una sola vez. Los pagos aparecen en
**Facturación** (administrador).

## Frontend (React + TypeScript)

La SPA está en `frontend/`: **React 18 + TypeScript**, estado global con **Redux Toolkit**,
llamadas a la API con **RTK Query** (caché e invalidación por etiquetas), rutas con
**React Router** y build con **Vite**. En Docker se compila y la sirve nginx.

| Carpeta | Contenido |
|---|---|
| `src/api` | Contrato de la API (`tipos.ts`), `baseQuery` (token, 401, renovación) y endpoints |
| `src/app` | Store de Redux, hooks y rutas del menú por rol |
| `src/features` | Slices: sesión (persistida), interfaz (avisos y tema) y reserva en curso |
| `src/components` | Layout, filas de turnos, avisos y componentes comunes |
| `src/pages` | Una pantalla por archivo |

Para trabajar en el frontend sin Docker (con el backend levantado):

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
npm test           # pruebas unitarias (Vitest)
npm run lint       # ESLint
npm run build      # build de producción en dist/
```

## Simular fallas de los sistemas externos y del broker

Sin reiniciar nada (se deshacen cambiando `:add(value=…)` por `:remove`). En Git Bash
anteponer `MSYS_NO_PATHCONV=1` a cada comando, para que no convierta la ruta `/opt/...`.

```bash
# El legado de obras sociales tarda 7 s (más que el timeout de 5 s)
docker compose exec app /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/system-property=obrassociales.mock.demoraMs:add(value=7000)"

# La facturación del legado responde con SOAP Fault (reintentos de la cola y DLQ)
docker compose exec app /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/system-property=obrassociales.mock.fallar:add(value=true)"

# La plataforma de videollamadas responde 503
docker compose exec app /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/system-property=videollamadas.mock.fallar:add(value=true)"

# La pasarela de pagos responde 503 (el paciente ve que el servicio de pagos no está disponible)
docker compose exec app /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/system-property=pagos.mock.fallar:add(value=true)"

# Apagar y volver a prender el broker
docker compose stop broker
docker compose start broker
```

## Más documentación

- `deploy/README.md`: instalación manual en un WildFly propio (sin Docker) y todas las propiedades de configuración.
- `src/test/README-seguridad.md`: pruebas de seguridad automatizadas.
