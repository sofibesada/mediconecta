import type { Dispatch } from "@reduxjs/toolkit";
import { API_URL } from "../../api/config";
import { tokenRenovado } from "./sesionSlice";

/**
 * Renovación del token. El token dura 15 minutos. Si el usuario estuvo activo (clic o tecla)
 * en los últimos 15 minutos y al token le quedan menos de 5, se pide uno nuevo. Si deja la
 * pestaña abierta sin usarla, el token vence y la sesión se cierra (las consultas automáticas
 * de notificaciones no cuentan como actividad).
 */
export const RENOVAR_ANTES_MS = 5 * 60_000;
export const INACTIVIDAD_MAXIMA_MS = 15 * 60_000;

let ultimaActividad = Date.now();
let renovando = false;

export function registrarActividad(ahora = Date.now()) {
  ultimaActividad = ahora;
}

export function escucharActividad(doc: Document = document) {
  for (const ev of ["click", "keydown"]) doc.addEventListener(ev, () => registrarActividad(), true);
}

/** Vencimiento del JWT en milisegundos (claim exp). 0 si el token no se puede leer. */
export function vencimientoDelToken(token: string): number {
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(atob(payload)).exp * 1000;
  } catch {
    return 0;
  }
}

export function hayQueRenovar(token: string, ahora: number, actividad: number): boolean {
  if (vencimientoDelToken(token) - ahora > RENOVAR_ANTES_MS) return false;
  return ahora - actividad <= INACTIVIDAD_MAXIMA_MS;
}

export async function renovarSiHaceFalta(token: string | undefined, dispatch: Dispatch) {
  if (renovando || !token || !hayQueRenovar(token, Date.now(), ultimaActividad)) return;
  renovando = true;
  try {
    const r = await fetch(API_URL + "/usuarios/renovar", { method: "POST", headers: { Authorization: "Bearer " + token } });
    if (r.ok) dispatch(tokenRenovado((await r.json()).token));
  } catch {
    // Si no se pudo renovar, se intenta en el próximo pedido.
  } finally {
    renovando = false;
  }
}
