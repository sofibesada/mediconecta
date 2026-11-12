import { useEffect, useRef } from "react";
import { useToast } from "../app/hooks";
import { useNotificacionesQuery } from "../api/mediconectaApi";

export const CONSULTA_NOTIFICACIONES_MS = 10_000;

/**
 * Las notificaciones las genera el backend de forma asincrónica (MDB que escucha el tópico
 * JMS), así que el frontend no las recibe en la respuesta de confirmar o cancelar: las
 * consulta cada 10 segundos. Las nuevas aparecen como aviso; devuelve cuántas no se leyeron.
 */
export function useAvisosDeNotificaciones(activo: boolean): number {
  const toast = useToast();
  const { data } = useNotificacionesQuery(undefined, { skip: !activo, pollingInterval: CONSULTA_NOTIFICACIONES_MS });
  // ids ya vistos; null = todavía no llegó la primera respuesta (esas no se avisan).
  const conocidas = useRef<Set<number> | null>(null);

  useEffect(() => {
    if (!activo) conocidas.current = null;
  }, [activo]);

  useEffect(() => {
    if (!data) return;
    const anteriores = conocidas.current;
    if (anteriores) {
      data
        .filter((n) => !n.leida && !anteriores.has(n.id))
        .reverse()
        .forEach((n) => toast("🔔 " + n.titulo + " — " + n.mensaje, "info"));
    }
    conocidas.current = new Set(data.map((n) => n.id));
  }, [data, toast]);

  return activo && data ? data.filter((n) => !n.leida).length : 0;
}
