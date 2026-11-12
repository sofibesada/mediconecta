import type { Turno } from "../api/tipos";

/** Pacientes distintos que tienen (o tuvieron) un turno en la agenda del profesional. */
export function pacientesDe(turnos: Turno[] = []): { id: number; nombre: string }[] {
  const map = new Map<number, string>();
  for (const t of turnos) if (t.pacienteId) map.set(t.pacienteId, t.pacienteNombre || "Paciente #" + t.pacienteId);
  return [...map.entries()].map(([id, nombre]) => ({ id, nombre }));
}
