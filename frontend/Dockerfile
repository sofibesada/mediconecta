# Etapa 1: compila la SPA (TypeScript + Vite) a archivos estaticos.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
ARG VITE_API_URL=http://localhost:8080/mediconecta/api
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# Etapa 2: nginx sirve solo lo compilado (sin Node ni codigo fuente).
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
