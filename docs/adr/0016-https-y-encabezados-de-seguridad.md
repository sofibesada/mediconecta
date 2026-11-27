# ADR 0016 · Entrada única por nginx con HTTPS y encabezados de seguridad

- **Estado:** aceptada
- **Fecha:** 26/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

Para la entrega final la cátedra pide cifrado en tránsito (TLS), los encabezados CSP, HSTS y
X-Content-Type-Options, CORS sin comodín con credenciales y cookies Secure, HttpOnly y SameSite,
y lo verifica con OWASP ZAP. Hasta ahora:

- la SPA se servía por HTTP en el puerto 5173 y llamaba a la API en otro origen (8080), con CORS;
- a un origen desconocido la API le respondía `Access-Control-Allow-Origin: *`;
- no había CSP ni HSTS, y `index.html` tenía un script inline (el del tema);
- la cookie de sesión del asistente de agendamiento no era Secure ni SameSite.

## Decisión

**nginx es la única puerta del navegador**, en `https://localhost:8443`:

| Pieza | Qué hace |
|---|---|
| `frontend/nginx/default.conf` | HTTPS (TLS 1.2 y 1.3), sirve la SPA y reenvía `/mediconecta/api/` a WildFly. El puerto 80 (5173 en el host) redirige a HTTPS |
| `frontend/Dockerfile` | Genera un certificado autofirmado en cada build (nunca está en el repositorio). En un despliegue real se monta uno válido en `/etc/nginx/tls` |
| `frontend/nginx/seguridad.conf` | HSTS (1 año), X-Content-Type-Options, X-Frame-Options, Referrer-Policy, Permissions-Policy y Cross-Origin-Opener-Policy en todas las respuestas |
| `frontend/nginx/csp.conf` | CSP de la SPA: scripts y estilos propios (sin inline), fuentes de Google Fonts, `connect-src 'self'`, `frame-ancestors 'none'`. La API responde con `default-src 'none'` |
| `CorsFilter` | Sin comodín: solo los orígenes de desarrollo configurados (5173 con `npm run dev`) reciben CORS. En Docker la SPA y la API comparten el origen |
| `web.xml` y `undertow-handlers.conf` | Cookie de sesión `Secure`, `HttpOnly` y `SameSite=Strict` |
| `docker-compose.yml` | El puerto 8080 de WildFly queda solo para esta máquina (`127.0.0.1`): lo usan las pruebas de API y los paneles de los simuladores |

El tema guardado se aplica desde `/tema.js` (archivo propio) en lugar de un script inline.

## Alternativas consideradas

- **Solo encabezados, sin TLS.** Descartada: HSTS no tiene efecto por HTTP y la cátedra pide
  cifrado en tránsito.
- **TLS en WildFly.** Descartada: habría que mantener dos orígenes con CORS y el certificado en
  dos lugares. Con nginx al frente, WildFly no se expone al navegador.
- **Certificado de una CA local (mkcert).** Evita el aviso del navegador, pero obliga a instalar
  una CA en cada máquina; el build reproducible (G1) no puede depender de eso.

## Consecuencias

- La primera vez el navegador avisa que el certificado no es de confianza (es de prueba). Hay
  que aceptar el aviso; está explicado en el README y en el manual.
- HSTS en `localhost` puede hacer que el navegador pida HTTPS a otras aplicaciones locales en
  ese mismo nombre. Se borra desde la configuración del navegador si molesta.
- Las E2E y Lighthouse corren contra `https://localhost:8443` aceptando el certificado local.
  Las pruebas de API siguen yendo directo a WildFly por el puerto 8080 local.

## Cómo se verifica

- `JwtAuthenticationMechanismTest`: sin un origen conocido no hay encabezados CORS.
- Las 20 E2E y Lighthouse sobre HTTPS; un recorrido con Playwright sin violaciones de CSP (fuentes,
  tema y llamadas a la API).
- `curl -kI https://localhost:8443/` muestra HSTS, CSP y los demás encabezados;
  `curl -I http://localhost:5173/` responde 301 a HTTPS.
