import { combineReducers, configureStore, createListenerMiddleware, isAnyOf } from "@reduxjs/toolkit";
import { mediconectaApi } from "../api/mediconectaApi";
import agenda from "../features/agenda/agendaSlice";
import sesion, { CLAVE_SESION, leerSesionGuardada, sesionCerrada, sesionExpirada } from "../features/sesion/sesionSlice";
import ui, { CLAVE_TEMA, leerTemaGuardado } from "../features/ui/uiSlice";

const rootReducer = combineReducers({
  sesion,
  ui,
  agenda,
  [mediconectaApi.reducerPath]: mediconectaApi.reducer,
});

export type RootState = ReturnType<typeof rootReducer>;

/** Al cerrar la sesión se borra todo lo que quedó en caché del usuario anterior. */
const listener = createListenerMiddleware();
listener.startListening({
  matcher: isAnyOf(sesionCerrada, sesionExpirada),
  effect: (_action, api) => {
    api.dispatch(mediconectaApi.util.resetApiState());
  },
});

export function crearStore(estadoInicial?: Partial<RootState>) {
  return configureStore({
    reducer: rootReducer,
    preloadedState: estadoInicial,
    middleware: (gdm) => gdm().prepend(listener.middleware).concat(mediconectaApi.middleware),
    // RTK agrupa las notificaciones de RTK Query con requestAnimationFrame, que el navegador
    // no ejecuta con la pestaña en segundo plano: la pantalla quedaba en "Cargando…" aunque los
    // datos ya hubieran llegado. Con "tick" (microtarea) se actualiza siempre.
    enhancers: (gde) => gde({ autoBatch: { type: "tick" } }),
  });
}

/** Store de la aplicación: arranca con la sesión y el tema guardados en este navegador. */
export function crearStoreDeLaApp() {
  const store = crearStore({ sesion: leerSesionGuardada(), ui: { toasts: [], tema: leerTemaGuardado() } });
  let anterior = store.getState();
  store.subscribe(() => {
    const actual = store.getState();
    try {
      if (actual.sesion.usuario !== anterior.sesion.usuario) {
        if (actual.sesion.usuario) localStorage.setItem(CLAVE_SESION, JSON.stringify(actual.sesion.usuario));
        else localStorage.removeItem(CLAVE_SESION);
      }
      if (actual.ui.tema !== anterior.ui.tema) localStorage.setItem(CLAVE_TEMA, actual.ui.tema);
    } catch {
      // Sin localStorage (modo privado estricto): la sesión dura lo que dure la pestaña.
    }
    anterior = actual;
  });
  return store;
}

export type AppStore = ReturnType<typeof crearStore>;
export type AppDispatch = AppStore["dispatch"];
