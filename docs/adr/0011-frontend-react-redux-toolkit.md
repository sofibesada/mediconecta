# ADR 0011 · Frontend en React + TypeScript con Redux Toolkit

- **Estado:** aceptada (reemplaza la SPA en un solo `index.html`)
- **Fecha:** 13/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

Hasta la Entrega 4, la interfaz era un único `index.html` de unas 1.350 líneas, con HTML,
CSS y JavaScript sin framework. Se eligió así por velocidad, pero con el crecimiento
(3 roles, 13 pantallas, agendamiento con reserva temporal, avisos, cobertura y facturación)
aparecieron problemas:

- **El estado estaba en variables globales** (`usuario`, `turnoEnHold`, `historiaCtx`, ...),
  que cualquier función podía modificar.
- **Las pantallas se armaban con `innerHTML`**, así que había que escapar cada dato a mano
  para evitar XSS.
- **No había tipos, pruebas unitarias ni build.** Un error de nombre en un campo de la API
  aparecía recién en el navegador.

## Decisión

- **React 18 + TypeScript**, empaquetado con **Vite**. Una pantalla por archivo
  (`frontend/src/pages`) y componentes reutilizables (`FilaTurno`, `Layout`, ...).
- **Redux Toolkit** para el estado global, con un slice por tema:
  - `sesion`: el usuario y el token, guardados en `localStorage`;
  - `ui`: los avisos y el tema;
  - `agenda`: la reserva en curso.
- **RTK Query** para la API (`src/api/mediconectaApi.ts`), con un `baseQuery` propio que:
  - agrega `Authorization: Bearer`;
  - cierra la sesión ante un 401;
  - renueva el token si el usuario está activo y faltan menos de 5 minutos;
  - traduce los errores `{"error": ...}` y Problem Details a un mensaje para el usuario.

  Las consultas quedan en caché y las **etiquetas** (`Turnos`, `Notificaciones`, ...)
  indican qué volver a pedir después de cada cambio.
- **React Router** con guardas por sesión y por rol: sin sesión se va al ingreso, con la
  contraseña temporal solo se puede cambiarla, y con un rol ajeno se vuelve al inicio.
- **En Docker:** build en dos etapas (Node compila, nginx sirve), con *fallback* de rutas de
  la SPA y encabezados de seguridad.
- **En CI:** un job propio con ESLint, TypeScript, pruebas con Vitest (con cobertura) y
  build.

## Alternativas consideradas

- **Seguir con el `index.html`.** Cada pantalla nueva agrandaba el archivo y el riesgo.
- **React solo con Context / `useState`.** Alcanza para la sesión, pero la caché e
  invalidación de la API habría que hacerla a mano. RTK Query ya la trae.
- **Angular.** Es una opción sólida, pero necesita más configuración y la curva de
  aprendizaje del grupo era mayor.

## Consecuencias

- **React escapa el contenido por defecto:** desaparece el riesgo de XSS por `innerHTML`.
- **Los tipos del contrato** (`src/api/tipos.ts`) detectan en compilación un campo mal
  escrito.
- **Se mantuvieron los `id` y textos de la interfaz anterior**, así que las pruebas E2E
  existentes siguieron funcionando.
- **Mejoras que salieron en la migración:**
  - menú navegable en el celular, sin desplazamiento horizontal a 360 px;
  - Enter envía los formularios;
  - contraste WCAG AA en los tres temas;
  - soporte de `prefers-reduced-motion`.
- **Problema encontrado:** RTK agrupa por defecto las notificaciones del store con
  `requestAnimationFrame`, que no corre con la pestaña en segundo plano. Se configuró
  `autoBatch: { type: "tick" }` para que la pantalla siempre se actualice.
- **Se agrega Node 22** como dependencia de build (solo para compilar; en producción sirve
  nginx).

## Cómo se verifica

- **Vitest (`npm test`):** `baseQuery` (token, 401, errores, sin conexión), sesión
  persistida, renovación del token, formato de fechas e importes.
- **Playwright (`e2e/`):** los 13 escenarios E2E, incluidos responsive (E2E-09), axe sin
  problemas graves (E2E-10) y temas (E2E-11).
- **Lighthouse:** 90 o más en accesibilidad.
