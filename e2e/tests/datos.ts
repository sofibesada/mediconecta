import { APIRequestContext, Page, expect } from "@playwright/test";

/**
 * Preparacion de datos por la API (rapido y sin depender de la UI) para que cada prueba
 * arranque desde el estado que necesita. Lo que se prueba despues se hace por la UI.
 */
export const API = process.env.API_URL ?? "http://localhost:8080/mediconecta/api";
const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? "admin@mediconecta.com";
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? "admin1234";

export type Sesion = { id: number; nombre: string; email: string; password: string; token: string };

let contador = 0;
export function emailUnico(prefijo: string) {
  return `${prefijo}_${Date.now()}_${process.pid}_${++contador}@e2e.test`;
}

async function post(request: APIRequestContext, ruta: string, body: unknown, token?: string) {
  const r = await request.post(API + ruta, {
    data: body,
    headers: token ? { Authorization: "Bearer " + token } : {},
  });
  expect(r.ok(), `${ruta} -> ${r.status()} ${await r.text()}`).toBeTruthy();
  return r.json();
}

export async function login(request: APIRequestContext, email: string, password: string) {
  return post(request, "/usuarios/login", { email, password });
}

export async function nuevoPaciente(request: APIRequestContext, nombre = "Paciente E2E"): Promise<Sesion> {
  const email = emailUnico("pac");
  const password = "clave1234";
  const u = await post(request, "/usuarios", { nombre, email, password });
  return { id: u.id, nombre, email, password, token: u.token };
}

/** Profesional dado de alta por el admin y con la contrasena temporal ya cambiada. */
export async function nuevoProfesional(request: APIRequestContext, nombre = "Dra. E2E"): Promise<Sesion> {
  const admin = await login(request, ADMIN_EMAIL, ADMIN_PASSWORD);
  const email = emailUnico("prof");
  const p = await post(request, "/admin/profesionales", { nombre, email, passwordTemporal: "temp1234" }, admin.token);
  const temporal = await login(request, email, "temp1234");
  const u = await post(request, "/usuarios/cambiar-password", { passwordActual: "temp1234", passwordNueva: "nueva1234" }, temporal.token);
  return { id: p.id, nombre, email, password: "nueva1234", token: u.token };
}

/** Fecha local (sin zona) a "horas" horas de ahora, redondeada a la hora y con minutos distintos por llamada. */
export function enHoras(horas: number) {
  const d = new Date(Date.now() + horas * 3_600_000);
  d.setUTCMinutes((++contador * 7) % 60, 0, 0);
  return d.toISOString().slice(0, 19);
}

export async function publicarHorario(request: APIRequestContext, prof: Sesion, horas: number, modalidad = "PRESENCIAL") {
  return post(request, "/turnos", { fechaHora: enHoras(horas), modalidad }, prof.token);
}

/** Turno reservado y confirmado como particular por el paciente. */
export async function turnoConfirmado(request: APIRequestContext, prof: Sesion, pac: Sesion, horas: number) {
  const t = await publicarHorario(request, prof, horas);
  await post(request, `/turnos/${t.id}/reservar`, {}, pac.token);
  await post(request, `/turnos/${t.id}/confirmar?particular=true`, {}, pac.token);
  return t;
}

/** Abre la app con la sesion iniciada (como si el usuario hubiera ingresado antes). */
export async function abrirConSesion(page: Page, request: APIRequestContext, s: Sesion) {
  const u = await login(request, s.email, s.password);
  await page.addInitScript((valor) => window.localStorage.setItem("mc_user", valor), JSON.stringify(u));
  await page.goto("/");
  await expect(page.locator("#dashboard")).toHaveClass(/activa/);
}

export async function ingresarPorUI(page: Page, email: string, password: string) {
  await page.goto("/");
  await page.getByLabel("Email").first().fill(email);
  await page.getByLabel("Contraseña").first().fill(password);
  await page.locator("#btnLogin").click();
}

export function toast(page: Page, tipo: "ok" | "err" | "info", texto: string | RegExp) {
  return page.locator(`.toast.${tipo}`, { hasText: texto });
}

export async function irA(page: Page, opcion: string) {
  await page.locator("#navPrincipal").getByText(opcion, { exact: true }).click();
}
