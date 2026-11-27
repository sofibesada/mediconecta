# Etapa 1: compila la SPA (TypeScript + Vite) a archivos estaticos.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# La API se sirve en el mismo origen que la SPA (nginx la reenvia a WildFly).
ARG VITE_API_URL=/mediconecta/api
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# Etapa 2: nginx sirve solo lo compilado (sin Node ni codigo fuente) y termina HTTPS.
FROM nginx:1.27-alpine
# Certificado autofirmado para el entorno local, generado en cada build (nunca en el repo).
# En un despliegue real se monta uno valido en /etc/nginx/tls.
RUN apk add --no-cache openssl \
 && mkdir -p /etc/nginx/tls /etc/nginx/snippets \
 && openssl req -x509 -nodes -newkey rsa:2048 -days 825 -subj "/CN=localhost" \
      -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
      -keyout /etc/nginx/tls/clave.pem -out /etc/nginx/tls/certificado.pem \
 && chmod 600 /etc/nginx/tls/clave.pem \
 && apk del openssl
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/csp.conf /etc/nginx/conf.d/00-csp.conf
COPY nginx/seguridad.conf /etc/nginx/snippets/seguridad.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80 443
