// Tarjetas de crédito y débito: marca por el prefijo (BIN), formato, dígito verificador
// (Luhn), vencimiento y código de seguridad. Mismas reglas que valida el backend y la pasarela.

export type Marca = "Visa" | "Mastercard" | "American Express" | "Diners Club" | "Naranja" | "Cabal" | "Maestro";

export function soloDigitos(s: string): string {
  return s.replace(/\D/g, "");
}

/** Marca según los primeros dígitos, o null si todavía no se reconoce. */
export function marca(numero: string): Marca | null {
  const n = soloDigitos(numero);
  if (/^3[47]/.test(n)) return "American Express";
  if (/^3(0[0-5]|[689])/.test(n)) return "Diners Club";
  if (/^589562/.test(n)) return "Naranja";
  if (/^(589657|6042|6043|6271)/.test(n)) return "Cabal";
  if (/^(5018|5020|5038|6304|6759|676[1-3])/.test(n)) return "Maestro";
  if (/^(5[1-5]|222[1-9]|22[3-9]\d|2[3-6]\d{2}|27[01]\d|2720)/.test(n)) return "Mastercard";
  if (/^4/.test(n)) return "Visa";
  return null;
}

/** Largo máximo de dígitos para la marca (para cortar lo que se tipea). */
export function largoMaximo(m: Marca | null): number {
  if (m === "American Express") return 15;
  if (m === "Diners Club") return 14;
  if (m === "Maestro" || m === "Visa") return 19;
  return 16;
}

export function largoValido(m: Marca, largo: number): boolean {
  switch (m) {
    case "American Express":
      return largo === 15;
    case "Diners Club":
      return largo === 14;
    case "Maestro":
      return largo >= 12 && largo <= 19;
    case "Visa":
      return largo === 13 || largo === 16 || largo === 19;
    default:
      return largo === 16;
  }
}

/** "4111111111111111" -> "4111 1111 1111 1111" (American Express: 4-6-5; Diners: 4-6-4). */
export function formatear(numero: string): string {
  const m = marca(numero);
  const n = soloDigitos(numero).slice(0, largoMaximo(m));
  const grupos = m === "American Express" ? [4, 6, 5] : m === "Diners Club" ? [4, 6, 4] : [4, 4, 4, 4, 3];
  const partes: string[] = [];
  let i = 0;
  for (const g of grupos) {
    if (i >= n.length) break;
    partes.push(n.slice(i, i + g));
    i += g;
  }
  return partes.join(" ");
}

/** Dígito verificador de Luhn: detecta errores de tipeo. */
export function luhn(numero: string): boolean {
  const n = soloDigitos(numero);
  if (!n) return false;
  let suma = 0;
  for (let i = 0; i < n.length; i++) {
    let d = Number(n[n.length - 1 - i]);
    if (i % 2 === 1) {
      d *= 2;
      if (d > 9) d -= 9;
    }
    suma += d;
  }
  return suma % 10 === 0;
}

/** "0128" -> "01/28" mientras se escribe. */
export function formatearVencimiento(v: string): string {
  const d = soloDigitos(v).slice(0, 4);
  return d.length > 2 ? d.slice(0, 2) + "/" + d.slice(2) : d;
}

/** MM/AA vigente (válido hasta el último día del mes). */
export function vigente(vencimiento: string, hoy = new Date()): boolean {
  const m = /^(0[1-9]|1[0-2])\/(\d{2})$/.exec(vencimiento);
  if (!m) return false;
  const anio = 2000 + Number(m[2]);
  const mes = Number(m[1]);
  return anio > hoy.getFullYear() || (anio === hoy.getFullYear() && mes >= hoy.getMonth() + 1);
}

export function largoCvv(m: Marca | null): number {
  return m === "American Express" ? 4 : 3;
}

/** Primer error de la tarjeta (para mostrar), o null si está todo bien. */
export function errorDeTarjeta(d: { numero: string; vencimiento: string; cvv: string; titular: string }, hoy = new Date()): string | null {
  const m = marca(d.numero);
  const n = soloDigitos(d.numero);
  if (!m || !largoValido(m, n.length) || !luhn(n)) return "El número de tarjeta no es válido";
  if (!vigente(d.vencimiento, hoy)) return "La tarjeta está vencida o la fecha no es válida (MM/AA)";
  if (!new RegExp(`^\\d{${largoCvv(m)}}$`).test(d.cvv)) return `El código de seguridad tiene ${largoCvv(m)} dígitos`;
  if (!d.titular.trim()) return "Ingresá el nombre del titular como figura en la tarjeta";
  return null;
}

/** Tarjetas de prueba de la pasarela simulada (todas con dígito verificador válido). */
export const TARJETAS_DE_PRUEBA: { numero: string; resultado: string }[] = [
  { numero: "4111 1111 1111 1111", resultado: "Visa: aprobada" },
  { numero: "5555 5555 5555 4444", resultado: "Mastercard: aprobada" },
  { numero: "3782 822463 10005", resultado: "American Express: aprobada" },
  { numero: "6042 0100 0000 0006", resultado: "Cabal: aprobada" },
  { numero: "5895 6200 0000 0002", resultado: "Naranja: aprobada" },
  { numero: "4000 0000 0000 0002", resultado: "Visa: rechazada por el banco" },
  { numero: "4000 0000 0000 9995", resultado: "Visa: fondos insuficientes" },
];
