import type { AppDispatch, RootState } from "../../app/store";
import { mediconectaApi } from "../../api/mediconectaApi";
import { sesionCerrada } from "./sesionSlice";

/**
 * Cierra la sesión. Si el paciente dejó un turno retenido sin confirmar, primero lo libera
 * (termina la conversación del asistente de agendamiento) para que otro lo pueda tomar.
 */
export const salir = () => (dispatch: AppDispatch, getState: () => RootState) => {
  if (getState().agenda.turnoRetenido) {
    dispatch(mediconectaApi.endpoints.abandonarReserva.initiate()).catch(() => undefined);
  }
  dispatch(sesionCerrada());
};
