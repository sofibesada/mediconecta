// Contrato de la API REST de MediConecta (los DTO que devuelve el backend).

export type Rol = "PACIENTE" | "PROFESIONAL" | "ADMIN";

export type Usuario = {
  id: number;
  nombre: string;
  email: string;
  rol: Rol;
  activo?: boolean;
  debeCambiarPassword?: boolean;
  token?: string;
  obraSocial?: string;
  numeroAfiliado?: string;
  planObraSocial?: string;
  copagoPorcentaje?: number;
};

export type EstadoTurno = "DISPONIBLE" | "RESERVADO_TEMPORAL" | "CONFIRMADO" | "CANCELADO" | "FINALIZADO";
export type Modalidad = "PRESENCIAL" | "VIRTUAL";

export type Turno = {
  id: number;
  fechaHora: string;
  estado: EstadoTurno;
  modalidad: Modalidad;
  profesionalId: number;
  profesionalNombre?: string;
  pacienteId?: number | null;
  pacienteNombre?: string | null;
  urgente?: boolean;
  holdExpiraEn?: string | null;
  obraSocial?: string | null;
  codigoAutorizacion?: string | null;
  importeCopago?: number | null;
  /** Version del turno: el ETag es este numero entre comillas (se manda en If-Match). */
  version?: number | null;
};

/** Lo que recuerda el AsistenteAgendamiento (@Stateful) de la conversación con el paciente. */
export type EstadoAsistente = {
  asistenteId: number;
  pedidosAtendidos: number;
  profesionalId?: number | null;
  horarios?: Turno[];
  turnoRetenido?: Turno | null;
};

export type Profesional = { id: number; nombre: string };

export type Diagnostico = { id: number; descripcion: string; fecha: string };
export type Receta = { id: number; medicamento: string; indicaciones?: string; fecha: string };
export type HistoriaClinica = { diagnosticos: Diagnostico[]; recetas: Receta[] };

export type CoberturaReceta = { recetaId: number; cubierto: boolean; descuentoPorcentaje?: number };
export type FichaPaciente = {
  nombre: string;
  email: string;
  obraSocial?: string | null;
  planObraSocial?: string | null;
  numeroAfiliado?: string | null;
  copagoPorcentaje?: number | null;
  avisoCobertura?: string | null;
  recetas?: CoberturaReceta[];
};

export type Notificacion = { id: number; titulo: string; mensaje: string; leida: boolean; creadaEn: string };

export type ObraSocial = { codigo: string; nombre: string; tipo?: string };

export type Sala = { url: string; rol: "ANFITRION" | "INVITADO" | string };

export type Autorizacion = {
  turnoId: number;
  obraSocial: string;
  codigoAutorizacion?: string | null;
  motivo?: string | null;
  fecha: string;
  resultado: string;
};
export type Reclamo = {
  turnoId: number;
  obraSocial: string;
  importe: number;
  codigoAutorizacion: string;
  numeroReclamo?: string | null;
  motivo?: string | null;
  creadoEn: string;
  estado: string;
};
export type Facturacion = { autorizaciones: Autorizacion[]; reclamos: Reclamo[] };

export type ProfesionalCreado = { id: number; email: string; nombre: string };
export type ResetPassword = { passwordTemporal: string };
