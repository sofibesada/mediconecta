# MediConecta

Plataforma que conecta pacientes con profesionales de la salud: turnos presenciales y
teleconsultas, cobertura de obras sociales, avisos y facturación.
Grupo 4 · Desarrollo de Aplicaciones II · UADE · 2.º cuatrimestre 2026.

## Levantar todo el sistema (un solo comando)

Requisito: **Docker** con Docker Compose (Docker Desktop en Windows/Mac). Nada más:
Java, Maven, WildFly, PostgreSQL y el broker corren dentro de los contenedores.

```bash
scripts/iniciar.sh
```

En Windows, desde Git Bash con ese mismo comando, o desde PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\iniciar.ps1
```

El script:

1. crea el `.env` a partir de `.env.example` si no existe (el `.env` no se sube al repositorio);
2. completa con valores al azar las claves obligatorias que estén vacías: las contraseñas de las
   cuentas de servicio del broker (SEC-07) y las claves que cifran los datos clínicos y los
   respaldos (SEC-10). Nunca cambia un valor que ya existe y no los muestra por pantalla;
3. corre `docker compose up --build` y espera a que todos los servicios estén listos.

Se puede correr todas las veces que haga falta. La primera vez compila y descarga las imágenes
(unos 5 minutos según la conexión).

Guarden una copia de `MEDICONECTA_CLAVE_DATOS` y `MEDICONECTA_CLAVE_RESPALDOS` fuera de la
máquina: si se pierden, los diagnósticos y recetas guardados no se pueden volver a leer y los
respaldos no se restauran.

Sin el script: copiar `.env.example` como `.env`, completar esas cuatro claves
(`openssl rand -base64 32` para las dos de cifrado) y correr `docker compose up --build`.

| Qué | Dónde |
|---|---|
| **Aplicación** | **https://localhost:8443** (el certificado es de prueba: la primera vez el navegador avisa; elegir «Avanzado» → «Continuar») |
| API REST (misma dirección que la app) | https://localhost:8443/mediconecta/api |
| http://localhost:5173 | Redirige a la aplicación en HTTPS |
| Sistema legado de obras sociales (SOAP) | http://localhost:8080/obras-sociales-mock/ObrasSociales?wsdl |
| Panel del legado (obras sociales, afiliados, vademécum) | http://localhost:8080/obras-sociales-mock/admin.html |
| Plataforma de videollamadas (REST, simulada) | http://localhost:8080/videollamadas-mock/api/v1/rooms |
| Pasarela de pagos con tarjeta (REST, simulada) | http://localhost:8080/pagos-mock/api/v1/pagos |
| Consola del broker ActiveMQ Artemis (colas, DLQ, reproceso) | http://localhost:8161/console |

El navegador entra solo por nginx, con HTTPS: la SPA y la API comparten el origen (sin CORS) y
cada respuesta lleva HSTS, Content-Security-Policy y los demás encabezados de seguridad
(`frontend/nginx/`). El puerto 8080 de WildFly queda abierto solo para esta máquina: lo usan las
pruebas de API y los paneles de los simuladores. En un despliegue real se monta un certificado
válido en `/etc/nginx/tls` del contenedor `frontend`.

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

### Cartilla: qué obras sociales atiende cada profesional

Cada profesional elige en **Mi Perfil** las obras sociales que atiende (por defecto, todas). En
**Agendar turno** el paciente ve si el profesional atiende la suya y puede mostrar solo a los que
sí; si no la atiende, el turno se confirma como particular. El servidor lo exige igual (409).

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

## Respaldos cifrados de la base

```bash
bash scripts/respaldo.sh
```

Deja `respaldos/mediconecta-AAAAMMDD-HHMMSS.dump.enc`, cifrado con AES-256 y con un `.hmac`
para detectar si alguien lo modificó. Usa `MEDICONECTA_CLAVE_RESPALDOS` del `.env` y conserva
los últimos 7. Para probar un respaldo sin tocar la base real:

```bash
bash scripts/restaurar.sh respaldos/<archivo>.dump.enc --base prueba_restauracion
```

Restaurar sobre la base real pide además `--si`. Para leer los diagnósticos y recetas de un
respaldo hacen falta **las dos claves**: la de respaldos y la `MEDICONECTA_CLAVE_DATOS` que
estaba en uso cuando se hizo. Guárdenlas fuera de la máquina y separadas de los respaldos.

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
# ...o falla solo los próximos 2 pedidos: la cola reintenta (3 intentos, cada 2 s) y la sala se crea igual
docker compose exec app /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/system-property=videollamadas.mock.fallarPedidos:add(value=2)"

# La pasarela de pagos responde 503 (el paciente ve que el servicio de pagos no está disponible)
docker compose exec app /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/system-property=pagos.mock.fallar:add(value=true)"

# El orientador de IA tarda 8 s (el gateway corta a los 5 s y responde el respaldo)
docker compose exec app /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/system-property=mediconecta.ia.simularFalla:add(value=lento)"
# ...o directamente no responde (value=caido)

# Apagar y volver a prender el broker
docker compose stop broker
docker compose start broker
```

## Asistente de salud (IA)

Desde cualquier pantalla, el paciente toca «¿Qué te pasa?» (abajo a la derecha), le cuenta
al asistente qué le pasa y recibe la especialidad sugerida con un botón para sacar turno
(`POST /ia/orientacion`). Cada mensaje se evalúa solo: no se le manda el historial a la IA.
Todo pasa por un gateway que:

- detecta urgencias e indica la guardia sin consultar a la IA;
- no manda los intentos de cambiar las instrucciones;
- oculta correos, DNI y teléfonos;
- valida la respuesta;
- corta a los 5 s;
- permite 5 consultas por minuto por paciente (después responde 429).

Ver [ADR 0015](docs/adr/0015-gateway-de-ia.md).

Por defecto usa un **proveedor simulado**: funciona sin internet y sin costo. Para usar
**Claude** (Anthropic), en el `.env`:

```bash
MEDICONECTA_IA_PROVEEDOR=claude
ANTHROPIC_API_KEY=...        # de console.anthropic.com; es personal y se cobra por uso
```

y `docker compose up -d app`. Si falta la clave, usa el simulado y lo avisa en el log.

## Más documentación

- `deploy/README.md`: instalación manual en un WildFly propio (sin Docker) y todas las propiedades de configuración.
- `src/test/README-seguridad.md`: pruebas de seguridad automatizadas.
