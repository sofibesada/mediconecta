import type { BaseQueryFn } from "@reduxjs/toolkit/query";
import type { SesionState } from "../features/sesion/sesionSlice";
import { sesionExpirada } from "../features/sesion/sesionSlice";
import { renovarSiHaceFalta } from "../features/sesion/token";
import { mostrarToast } from "../features/ui/uiSlice";
import { API_URL } from "./config";

export type Pedido = {
  url: string;
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  /**
   * true: el navegador manda la cookie de sesión (JSESSIONID). La usa el asistente de
   * agendamiento (@Stateful) para que cada pedido llegue a SU instancia.
   */
  sesion?: boolean;
  /** Encabezados extra (If-Match, Idempotency-Key). */
  headers?: Record<string, string>;
};

/** Error de la API tal como lo ve la interfaz: el código y el mensaje para el usuario. */
export type ErrorApi = { status: number | "SIN_CONEXION"; mensaje: string };

export const SIN_CONEXION = "No se pudo conectar con el servidor (¿WildFly está levantado en localhost:8080?)";

function mensajeDe(data: unknown, status: number): string {
  if (data && typeof data === "object") {
    const d = data as { error?: string; detail?: string; title?: string };
    if (d.error) return d.error;
    if (d.detail) return d.detail;
    if (d.title) return d.title;
  }
  if (typeof data === "string" && data) return data;
  return "Error " + status;
}

/**
 * Cada pedido lleva el token del login: el backend sabe quién es el usuario por el token,
 * no por los ids que manda el navegador. Los errores llegan en formato Problem Details
 * (RFC 9457): se muestra "error" o "detail".
 */
export const baseQuery: BaseQueryFn<string | Pedido, unknown, ErrorApi> = async (arg, api) => {
  const { url, method = "GET", body, sesion = false, headers: extra } = typeof arg === "string" ? ({ url: arg } as Pedido) : arg;
  const token = (api.getState() as { sesion: SesionState }).sesion.usuario?.token;

  const headers: Record<string, string> = { ...extra };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers.Authorization = "Bearer " + token;

  let res: Response;
  try {
    res = await fetch(API_URL + url, {
      method,
      headers,
      credentials: sesion ? "include" : "same-origin",
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: api.signal,
    });
  } catch {
    return { error: { status: "SIN_CONEXION", mensaje: SIN_CONEXION } };
  }

  const texto = await res.text();
  let data: unknown = null;
  if (texto) {
    try {
      data = JSON.parse(texto);
    } catch {
      data = texto;
    }
  }

  if (res.status === 401 && token) {
    // El token venció o la cuenta fue desactivada: se cierra la sesión.
    const mensaje = mensajeDe(data, 401) === "Error 401" ? "Tu sesión venció. Volvé a iniciar sesión." : mensajeDe(data, 401);
    api.dispatch(sesionExpirada(mensaje));
    api.dispatch(mostrarToast(mensaje, "err"));
    return { error: { status: 401, mensaje } };
  }
  if (!res.ok) return { error: { status: res.status, mensaje: mensajeDe(data, res.status) } };

  void renovarSiHaceFalta(token, api.dispatch);
  return { data };
};

/** Mensaje legible de cualquier error (de la API o de JavaScript). */
export function mensajeDeError(e: unknown): string {
  if (e && typeof e === "object" && "mensaje" in e) return String((e as ErrorApi).mensaje);
  if (e instanceof Error) return e.message;
  return "Ocurrió un error inesperado";
}
