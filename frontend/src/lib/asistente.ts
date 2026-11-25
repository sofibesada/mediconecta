import type { Orientacion } from "../api/tipos";

/** Se conservan los últimos mensajes: la charla del asistente es una ayuda, no un historial. */
export const MAX_MENSAJES = 20;

export type Mensaje =
  | { id: number; de: "paciente"; texto: string }
  | { id: number; de: "asistente"; texto: string; orientacion?: Orientacion; error?: boolean };

/** Agrega un mensaje y deja solo los últimos MAX_MENSAJES. */
export function agregar(lista: Mensaje[], m: Mensaje): Mensaje[] {
  const nueva = [...lista, m];
  return nueva.length > MAX_MENSAJES ? nueva.slice(nueva.length - MAX_MENSAJES) : nueva;
}
