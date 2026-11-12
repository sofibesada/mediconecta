# MediConecta

Plataforma que conecta pacientes con profesionales de la salud: turnos presenciales y
teleconsultas, cobertura de obras sociales, avisos y facturación.
Grupo 4 · Desarrollo de Aplicaciones II · UADE · 2.º cuatrimestre 2026.

## Levantar todo el sistema (un solo comando)

Requisito: **Docker** con Docker Compose (Docker Desktop en Windows/Mac). Nada más:
Java, Maven, WildFly, PostgreSQL y el broker corren dentro de los contenedores.

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
| Consola del broker ActiveMQ Artemis (colas, DLQ, reproceso) | http://localhost:8161/console |

Para apagar: `docker compose down` (agregar `-v` para borrar también la base de datos).

### Servicios

| Servicio | Imagen | Rol |
|---|---|---|
| `db` | PostgreSQL 16 | Base de datos |
| `broker` | ActiveMQ Artemis 2.39 | Tópico `TurnosEventosTopic`, cola `FacturacionQueue` y `DLQ` |
| `app` | WildFly 41 (perfil full) | MediConecta + los simuladores de videollamadas (REST) y obras sociales (SOAP) |
| `frontend` | nginx 1.27 (build con Node 22) | La SPA en React (ver más abajo) |

### Credenciales del entorno local de demo

Son valores **solo para este entorno local** y están en `docker-compose.yml`. Para
cambiarlos sin tocar el repo, copiar `.env.example` como `.env` y completarlo.

| Uso | Usuario | Clave |
|---|---|---|
| Administrador de MediConecta | `admin@mediconecta.com` | `admin1234` |
| Panel del legado de obras sociales | — | `padron-demo` |
| Consola de Artemis | `artemis` | `artemis` |

Los pacientes se registran desde la aplicación; los profesionales los crea el administrador.

### Datos de prueba del sistema de obras sociales

| Obra social | Afiliado | Resultado |
|---|---|---|
| OSMED | `12345678` | Cubierto y autorizado |
| OSMED | `12345679` | Dado de baja: no se puede cargar la cobertura |
| PREVENIR | `12345677` | Tope alcanzado: la autorización se rechaza |

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

# Apagar y volver a prender el broker
docker compose stop broker
docker compose start broker
```

## Más documentación

- `deploy/README.md`: instalación manual en un WildFly propio (sin Docker) y todas las propiedades de configuración.
- `src/test/README-seguridad.md`: pruebas de seguridad automatizadas.
