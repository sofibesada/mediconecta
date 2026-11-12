import type { EstadoTurno, Modalidad, Rol } from "../api/tipos";

export function rolLindo(r?: Rol | string | null): string {
  return ({ PACIENTE: "Paciente", PROFESIONAL: "Profesional", ADMIN: "Administrador" } as Record<string, string>)[r ?? ""] ?? r ?? "";
}

export function iniciales(nombre?: string | null): string {
  return (
    (nombre ?? "")
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((s) => s[0] ?? "")
      .join("")
      .toUpperCase() || "MC"
  );
}

export function modalidadLinda(m?: Modalidad | string | null): string {
  return m === "VIRTUAL" ? "🎥 Teleconsulta" : "Presencial";
}

export function estadoLindo(e: EstadoTurno | string): string {
  return e.replace("_", " ");
}

/** "viernes, 14 de junio, 10:00" (y el año si no es el actual). */
export function fmtFecha(iso?: string | null, hoy = new Date()): string {
  if (!iso) return "—";
  const dt = new Date(iso);
  if (isNaN(dt.getTime())) return iso;
  const opciones: Intl.DateTimeFormatOptions = { weekday: "long", day: "numeric", month: "long", hour: "2-digit", minute: "2-digit" };
  if (dt.getFullYear() !== hoy.getFullYear()) opciones.year = "numeric";
  return dt.toLocaleString("es-AR", opciones);
}

export function fmtPesos(n?: number | null): string {
  return n == null ? "—" : "$ " + Number(n).toLocaleString("es-AR", { maximumFractionDigits: 2 });
}

/** Fecha y hora LOCAL en el formato de un input datetime-local (no toISOString(), que es UTC). */
export function fechaParaInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function esFutura(fechaInput: string, ahora = new Date()): boolean {
  return new Date(fechaInput) > ahora;
}

/** El backend espera segundos: "2030-06-14T10:00" -> "2030-06-14T10:00:00". */
export function conSegundos(fechaInput: string): string {
  return fechaInput.length === 16 ? fechaInput + ":00" : fechaInput;
}

/** Segundos que faltan hasta "hasta" (nunca negativo) y el texto mm:ss. */
export function cuentaRegresiva(hasta: string | null | undefined, ahora = Date.now()): { segundos: number; texto: string } {
  const segundos = Math.max(0, Math.floor((new Date(hasta ?? 0).getTime() - ahora) / 1000));
  const mm = String(Math.floor(segundos / 60)).padStart(2, "0");
  const ss = String(segundos % 60).padStart(2, "0");
  return { segundos, texto: `${mm}:${ss}` };
}
