import type { Modalidad, Turno } from "../api/tipos";

/** Horarios de la modalidad elegida y desde el día elegido (fecha local "AAAA-MM-DD"). */
export function filtrarHorarios(lista: Turno[], modalidad: "" | Modalidad, desde: string): Turno[] {
  return lista.filter((t) => (!modalidad || t.modalidad === modalidad) && (!desde || t.fechaHora.slice(0, 10) >= desde));
}

export type ResumenDePago = {
  /** Valor total de la consulta (lo que paga la obra social más lo que paga el paciente). */
  valorConsulta: number | null;
  /** Lo que cubre la obra social (0 si es particular). */
  cubreObraSocial: number;
  /** Lo que le toca pagar al paciente (la consulta entera si es particular, o el copago). */
  aPagar: number | null;
  pagado: boolean;
  /** true si se puede pagar online ahora: confirmado, con importe y sin pagar. */
  puedePagar: boolean;
};

/** Cuánto cuesta el turno, cuánto cubre la obra social y cuánto falta pagar. */
export function resumenDePago(t: Turno): ResumenDePago {
  const aPagar = t.importeCopago ?? null;
  const cubre = t.importeObraSocial ?? 0;
  const pagado = !!t.pago;
  const confirmado = t.estado === "CONFIRMADO" || t.estado === "FINALIZADO";
  return {
    valorConsulta: aPagar == null ? null : aPagar + cubre,
    cubreObraSocial: cubre,
    aPagar,
    pagado,
    puedePagar: confirmado && !pagado && aPagar != null && aPagar > 0,
  };
}
