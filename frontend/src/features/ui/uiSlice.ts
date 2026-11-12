import { createSlice, nanoid, type PayloadAction } from "@reduxjs/toolkit";

export type TipoToast = "ok" | "err" | "info";
export type Toast = { id: string; mensaje: string; tipo: TipoToast };
export type Tema = "estandar" | "claro" | "oscuro";
export type UiState = { toasts: Toast[]; tema: Tema };

export const CLAVE_TEMA = "mc_tema";

export function leerTemaGuardado(): Tema {
  try {
    const t = localStorage.getItem(CLAVE_TEMA);
    return t === "claro" || t === "oscuro" ? t : "estandar";
  } catch {
    return "estandar";
  }
}

const uiSlice = createSlice({
  name: "ui",
  initialState: { toasts: [], tema: "estandar" } as UiState,
  reducers: {
    mostrarToast: {
      reducer(state, action: PayloadAction<Toast>) {
        state.toasts.push(action.payload);
      },
      prepare(mensaje: string, tipo: TipoToast = "info") {
        return { payload: { id: nanoid(), mensaje, tipo } };
      },
    },
    quitarToast(state, action: PayloadAction<string>) {
      state.toasts = state.toasts.filter((t) => t.id !== action.payload);
    },
    temaElegido(state, action: PayloadAction<Tema>) {
      state.tema = action.payload;
    },
  },
});

export const { mostrarToast, quitarToast, temaElegido } = uiSlice.actions;
export default uiSlice.reducer;
