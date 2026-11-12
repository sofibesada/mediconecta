import type { Turno } from "../api/tipos";
import { pacientesDe } from "./pacientes";

const turno = (id: number, pacienteId: number | null, pacienteNombre?: string): Turno => ({
  id, fechaHora: "2030-01-01T10:00:00", estado: "CONFIRMADO", modalidad: "PRESENCIAL", profesionalId: 1, pacienteId, pacienteNombre,
});

it("lista cada paciente una sola vez y saltea los horarios sin paciente", () => {
  expect(pacientesDe([turno(1, 5, "Ana"), turno(2, null), turno(3, 5, "Ana"), turno(4, 9)])).toEqual([
    { id: 5, nombre: "Ana" },
    { id: 9, nombre: "Paciente #9" },
  ]);
  expect(pacientesDe()).toEqual([]);
});
