import { useCallback, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { mensajeDeError } from "../api/baseQuery";
import { mediconectaApi } from "../api/mediconectaApi";
import type { Usuario } from "../api/tipos";
import { mostrarToast, type TipoToast } from "../features/ui/uiSlice";
import type { AppDispatch, RootState } from "./store";

export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<RootState>();

/** Usuario con la sesión iniciada. Solo se usa en pantallas que exigen sesión. */
export function useUsuario(): Usuario {
  const u = useAppSelector((s) => s.sesion.usuario);
  if (!u) throw new Error("Esta pantalla necesita una sesión iniciada");
  return u;
}

export function useToast() {
  const dispatch = useAppDispatch();
  return useCallback((mensaje: string, tipo: TipoToast = "info") => dispatch(mostrarToast(mensaje, tipo)), [dispatch]);
}

/**
 * Ejecuta una acción del usuario: mientras corre, "ocupado" es true (el botón se deshabilita
 * y muestra "…"); si falla, el mensaje del error aparece en un toast.
 */
export function useAccion(): [(fn: () => Promise<unknown>) => Promise<void>, boolean] {
  const [ocupado, setOcupado] = useState(false);
  const toast = useToast();
  const ejecutar = useCallback(
    async (fn: () => Promise<unknown>) => {
      setOcupado(true);
      try {
        await fn();
      } catch (e) {
        toast(mensajeDeError(e), "err");
      } finally {
        setOcupado(false);
      }
    },
    [toast],
  );
  return [ejecutar, ocupado];
}

/**
 * Las notificaciones las genera el backend de forma asincrónica (un MDB que escucha el tópico
 * JMS): después de confirmar, cancelar o reprogramar, el aviso llega en instantes. Se vuelven
 * a pedir a los 1,5 s en lugar de esperar al próximo ciclo de consulta.
 */
export function useRevisarNotificacionesPronto() {
  const dispatch = useAppDispatch();
  return useCallback(() => {
    setTimeout(() => dispatch(mediconectaApi.util.invalidateTags(["Notificaciones"])), 1500);
  }, [dispatch]);
}

/** Nombre de una obra social a partir de su código (la lista viene del sistema legado). */
export function useNombreObraSocial(activo = true) {
  const { data } = mediconectaApi.useObrasSocialesQuery(undefined, { skip: !activo });
  return useCallback((codigo?: string | null) => data?.find((o) => o.codigo === codigo)?.nombre ?? codigo ?? "", [data]);
}
