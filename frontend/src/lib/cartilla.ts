/**
 * Cartilla de un profesional: las obras sociales que atiende.
 * null o undefined = todas (valor por defecto); [] = ninguna (solo particular).
 * La misma regla la exige el backend al confirmar el turno.
 */
export type CartillaDeProfesional = string[] | null | undefined;

export function atiende(cartilla: CartillaDeProfesional, obraSocial?: string | null): boolean {
  if (cartilla == null) return true;
  if (!obraSocial) return false;
  return cartilla.includes(obraSocial.toUpperCase());
}

/** Texto corto para mostrar: «Todas las obras sociales», «Solo particular» o la lista de nombres. */
export function resumenCartilla(cartilla: CartillaDeProfesional, nombre: (codigo: string) => string = (c) => c): string {
  if (cartilla == null) return "Todas las obras sociales";
  if (cartilla.length === 0) return "Solo particular";
  return cartilla.map(nombre).join(", ");
}
