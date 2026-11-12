// URL de la API. Se puede cambiar al compilar con VITE_API_URL (ver Dockerfile).
export const API_URL: string = import.meta.env.VITE_API_URL ?? "http://localhost:8080/mediconecta/api";
