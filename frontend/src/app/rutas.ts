import type { Rol } from "../api/tipos";

/** Pantallas del menú: ruta, texto y qué roles la ven. */
export type ItemMenu = { ruta: string; texto: string; roles: Rol[] };

export const MENU: ItemMenu[] = [
  { ruta: "/", texto: "Inicio", roles: ["PACIENTE", "PROFESIONAL", "ADMIN"] },
  { ruta: "/agendar", texto: "Agendar turno", roles: ["PACIENTE"] },
  { ruta: "/mis-citas", texto: "Mis Citas", roles: ["PACIENTE"] },
  { ruta: "/historia", texto: "Historia Clínica", roles: ["PACIENTE"] },
  { ruta: "/notificaciones", texto: "Notificaciones", roles: ["PACIENTE", "PROFESIONAL", "ADMIN"] },
  { ruta: "/mi-agenda", texto: "Mi Agenda", roles: ["PROFESIONAL"] },
  { ruta: "/pacientes", texto: "Mis Pacientes", roles: ["PROFESIONAL"] },
  { ruta: "/admin", texto: "Administración", roles: ["ADMIN"] },
  { ruta: "/facturacion", texto: "Facturación", roles: ["ADMIN"] },
  { ruta: "/perfil", texto: "Mi Perfil", roles: ["PACIENTE", "PROFESIONAL", "ADMIN"] },
];

export function menuDe(rol: Rol): ItemMenu[] {
  return MENU.filter((m) => m.roles.includes(rol));
}

/** Título del encabezado según la ruta. */
export function tituloDe(ruta: string): string {
  if (ruta === "/ingresar") return "MediConecta";
  if (ruta === "/cambiar-password") return "Cambio de contraseña";
  if (ruta === "/confirmacion") return "Confirmación";
  if (/^\/pacientes\/\d+\/historia$/.test(ruta)) return "Historia Clínica";
  return MENU.find((m) => m.ruta === ruta)?.texto ?? "MediConecta";
}
