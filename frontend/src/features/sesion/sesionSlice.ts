import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { Usuario } from "../../api/tipos";

/**
 * Sesión del usuario: sus datos y el token JWT que manda en cada pedido. Se guarda en el
 * localStorage (clave mc_user) para que al recargar la página siga con la sesión iniciada.
 */
export type SesionState = { usuario: Usuario | null };

export const CLAVE_SESION = "mc_user";
const CAMPOS_COBERTURA = ["obraSocial", "numeroAfiliado", "planObraSocial", "copagoPorcentaje"] as const;

export function leerSesionGuardada(): SesionState {
  try {
    const u = JSON.parse(localStorage.getItem(CLAVE_SESION) ?? "null") as Usuario | null;
    // Una sesión vieja sin token no sirve: el backend identifica al usuario por el token.
    return { usuario: u && u.token ? u : null };
  } catch {
    return { usuario: null };
  }
}

const sesionSlice = createSlice({
  name: "sesion",
  initialState: { usuario: null } as SesionState,
  reducers: {
    sesionIniciada(state, action: PayloadAction<Usuario>) {
      state.usuario = action.payload;
    },
    /** Datos nuevos del usuario (por ejemplo, la cobertura). El token no cambia. */
    usuarioActualizado(state, action: PayloadAction<Usuario>) {
      if (!state.usuario) return;
      const token = action.payload.token ?? state.usuario.token;
      const nuevo: Usuario = { ...state.usuario, ...action.payload, token };
      // Si el backend no manda la cobertura, es porque el usuario ya no tiene.
      for (const k of CAMPOS_COBERTURA) if (!(k in action.payload)) delete nuevo[k];
      state.usuario = nuevo;
    },
    tokenRenovado(state, action: PayloadAction<string>) {
      if (state.usuario) state.usuario.token = action.payload;
    },
    sesionCerrada(state) {
      state.usuario = null;
    },
    /** El backend rechazó el token (venció o la cuenta fue desactivada). */
    sesionExpirada(state, _action: PayloadAction<string>) {
      state.usuario = null;
    },
  },
});

export const { sesionIniciada, usuarioActualizado, tokenRenovado, sesionCerrada, sesionExpirada } = sesionSlice.actions;
export default sesionSlice.reducer;
