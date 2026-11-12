import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { Turno } from "../../api/tipos";
import { sesionCerrada, sesionExpirada } from "../sesion/sesionSlice";

/**
 * Reserva en curso del paciente. La fuente de verdad es el servidor (AsistenteAgendamiento,
 * @Stateful), esto es lo que la pantalla muestra: el turno retenido y, si la obra social
 * rechazó o no respondió al confirmar, el motivo (para ofrecer confirmar como particular).
 */
export type AgendaState = { turnoRetenido: Turno | null; avisoCobertura: string | null };

const inicial: AgendaState = { turnoRetenido: null, avisoCobertura: null };

const agendaSlice = createSlice({
  name: "agenda",
  initialState: inicial,
  reducers: {
    turnoRetenido(state, action: PayloadAction<Turno | null | undefined>) {
      state.turnoRetenido = action.payload ?? null;
      state.avisoCobertura = null;
    },
    coberturaRechazada(state, action: PayloadAction<string>) {
      state.avisoCobertura = action.payload;
    },
    reservaTerminada() {
      return inicial;
    },
  },
  extraReducers: (b) => {
    b.addCase(sesionCerrada, () => inicial).addCase(sesionExpirada, () => inicial);
  },
});

export const { turnoRetenido, coberturaRechazada, reservaTerminada } = agendaSlice.actions;
export default agendaSlice.reducer;
