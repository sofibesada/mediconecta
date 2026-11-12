import { useCallback } from "react";
import { useToast, useUsuario } from "../app/hooks";
import { useLazySalaQuery } from "../api/mediconectaApi";
import type { Turno } from "../api/tipos";

/** Abre la sala de Jitsi de un turno virtual, con el nombre del usuario ya cargado. */
export function useVideollamada() {
  const usuario = useUsuario();
  const toast = useToast();
  const [pedirSala] = useLazySalaQuery();

  return useCallback(
    async (t: Turno) => {
      // Se abre la pestaña ya (dentro del clic) para que el navegador no la bloquee como popup.
      const pestana = window.open("", "_blank");
      try {
        const sala = await pedirSala(t.id).unwrap();
        const nombre = "userInfo.displayName=" + encodeURIComponent(JSON.stringify(usuario.nombre || ""));
        const url = sala.url + (sala.url.includes("#") ? "&" : "#") + nombre;
        if (pestana) pestana.location.href = url;
        else window.location.href = url; // el navegador bloqueó la pestaña nueva
        toast(
          sala.rol === "ANFITRION"
            ? "Sos el anfitrión: si Jitsi te lo pide, iniciá sesión para abrir la reunión."
            : "Entrás como paciente: no hace falta iniciar sesión. Si el profesional todavía no llegó, vas a esperar unos segundos.",
          "info",
        );
      } catch {
        pestana?.close();
        // La sala la crea un consumidor JMS de forma asincrónica: puede tardar unos segundos.
        toast("La sala todavía se está preparando. Probá de nuevo en unos segundos.", "info");
      }
    },
    [pedirSala, toast, usuario.nombre],
  );
}
