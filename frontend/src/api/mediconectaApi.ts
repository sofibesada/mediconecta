import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQuery } from "./baseQuery";
import type {
  EstadoAsistente,
  Facturacion,
  FichaPaciente,
  HistoriaClinica,
  Modalidad,
  Notificacion,
  ObraSocial,
  Pago,
  Profesional,
  ProfesionalCreado,
  ResetPassword,
  Sala,
  Turno,
  Usuario,
} from "./tipos";

const ASISTENTE = "/asistente-agendamiento";

/** If-Match con la version del turno, si se conoce. */
function siHayVersion(version?: number | null): Record<string, string> | undefined {
  return version == null ? undefined : { "If-Match": `"${version}"` };
}

/**
 * Todos los endpoints del backend en un solo lugar (RTK Query). Las consultas quedan en
 * caché y las etiquetas (tags) indican qué hay que volver a pedir después de cada cambio:
 * por ejemplo, cancelar un turno invalida "Turnos" y las listas se actualizan solas.
 */
export const mediconectaApi = createApi({
  reducerPath: "api",
  baseQuery,
  tagTypes: ["Turnos", "Historia", "Notificaciones", "Usuarios", "Facturacion"],
  refetchOnMountOrArgChange: 30,
  endpoints: (b) => ({
    // ---------- usuarios ----------
    login: b.mutation<Usuario, { email: string; password: string }>({
      query: (body) => ({ url: "/usuarios/login", method: "POST", body }),
    }),
    registrar: b.mutation<Usuario, { nombre: string; email: string; password: string }>({
      query: (body) => ({ url: "/usuarios", method: "POST", body }),
    }),
    cambiarPassword: b.mutation<Usuario, { passwordActual: string; passwordNueva: string }>({
      query: (body) => ({ url: "/usuarios/cambiar-password", method: "POST", body }),
    }),

    // ---------- turnos ----------
    turnosDePaciente: b.query<Turno[], number>({
      query: (id) => "/turnos/paciente/" + id,
      providesTags: ["Turnos"],
    }),
    turnosDeProfesional: b.query<Turno[], number>({
      query: (id) => "/turnos/profesional/" + id,
      providesTags: ["Turnos"],
    }),
    publicarHorario: b.mutation<Turno, { fechaHora: string; modalidad: Modalidad }>({
      query: (body) => ({ url: "/turnos", method: "POST", body }),
      invalidatesTags: ["Turnos"],
    }),
    // If-Match con la version que se esta viendo: si otro cambio el turno, la API responde 412.
    reprogramarTurno: b.mutation<Turno, { id: number; fechaHora: string; version?: number | null }>({
      query: ({ id, fechaHora, version }) => ({ url: `/turnos/${id}/reprogramar`, method: "PUT", body: { fechaHora }, headers: siHayVersion(version) }),
      invalidatesTags: ["Turnos"],
    }),
    cancelarTurno: b.mutation<void, { id: number; version?: number | null }>({
      query: ({ id, version }) => ({ url: "/turnos/" + id, method: "DELETE", headers: siHayVersion(version) }),
      invalidatesTags: ["Turnos"],
    }),
    sala: b.query<Sala, number>({
      query: (id) => `/turnos/${id}/sala`,
      keepUnusedDataFor: 0,
    }),
    especialidades: b.query<string[], void>({
      query: () => "/profesionales/especialidades",
      keepUnusedDataFor: 3600,
    }),
    profesionales: b.query<Profesional[], void>({
      query: () => "/profesionales",
    }),

    // ---------- asistente de agendamiento (@Stateful, atado a la cookie de sesión) ----------
    estadoAsistente: b.query<EstadoAsistente, void>({
      query: () => ({ url: ASISTENTE, sesion: true }),
      keepUnusedDataFor: 0,
    }),
    elegirProfesional: b.mutation<EstadoAsistente, number>({
      query: (profesionalId) => ({ url: ASISTENTE + "/profesional", method: "PUT", body: { profesionalId }, sesion: true }),
    }),
    reservar: b.mutation<EstadoAsistente, number>({
      query: (turnoId) => ({ url: ASISTENTE + "/reserva", method: "POST", body: { turnoId }, sesion: true }),
    }),
    // Idempotency-Key: si el pedido se repite (doble clic, corte de red), el turno no se confirma dos veces.
    confirmar: b.mutation<Turno, { particular: boolean; clave: string }>({
      query: ({ particular, clave }) => ({
        url: ASISTENTE + "/confirmacion" + (particular ? "?particular=true" : ""),
        method: "POST",
        sesion: true,
        headers: { "Idempotency-Key": clave },
      }),
      invalidatesTags: ["Turnos"],
    }),
    abandonarReserva: b.mutation<void, void>({
      query: () => ({ url: ASISTENTE, method: "DELETE", sesion: true }),
    }),

    // ---------- historia clínica ----------
    historia: b.query<HistoriaClinica, number>({
      query: (pacienteId) => "/historia-clinica/" + pacienteId,
      providesTags: (_r, _e, id) => [{ type: "Historia", id }],
    }),
    ficha: b.query<FichaPaciente, number>({
      query: (pacienteId) => `/historia-clinica/${pacienteId}/ficha`,
      providesTags: (_r, _e, id) => [{ type: "Historia", id }],
    }),
    registrarDiagnostico: b.mutation<void, { pacienteId: number; descripcion: string }>({
      query: ({ pacienteId, descripcion }) => ({ url: `/historia-clinica/${pacienteId}/diagnosticos`, method: "POST", body: { descripcion } }),
      invalidatesTags: (_r, _e, a) => [{ type: "Historia", id: a.pacienteId }],
    }),
    registrarReceta: b.mutation<void, { pacienteId: number; medicamento: string; indicaciones: string }>({
      query: ({ pacienteId, ...body }) => ({ url: `/historia-clinica/${pacienteId}/recetas`, method: "POST", body }),
      invalidatesTags: (_r, _e, a) => [{ type: "Historia", id: a.pacienteId }],
    }),

    // ---------- notificaciones ----------
    notificaciones: b.query<Notificacion[], void>({
      query: () => "/notificaciones",
      providesTags: ["Notificaciones"],
    }),
    marcarLeida: b.mutation<void, number>({
      query: (id) => ({ url: `/notificaciones/${id}/leida`, method: "POST" }),
      invalidatesTags: ["Notificaciones"],
    }),
    marcarTodasLeidas: b.mutation<void, void>({
      query: () => ({ url: "/notificaciones/leer-todas", method: "POST" }),
      invalidatesTags: ["Notificaciones"],
    }),

    // ---------- cobertura (obras sociales, sistema legado por SOAP a través del backend) ----------
    obrasSociales: b.query<ObraSocial[], void>({
      query: () => "/cobertura/obras-sociales",
      keepUnusedDataFor: 3600,
    }),
    guardarCobertura: b.mutation<Usuario, { obraSocial: string; numeroAfiliado: string }>({
      query: (body) => ({ url: "/cobertura", method: "PUT", body }),
    }),
    quitarCobertura: b.mutation<Usuario, void>({
      query: () => ({ url: "/cobertura", method: "DELETE" }),
    }),

    // ---------- administración ----------
    usuarios: b.query<Usuario[], void>({
      query: () => "/admin/usuarios",
      providesTags: ["Usuarios"],
    }),
    // ---------- perfil propio ----------
    actualizarPerfil: b.mutation<Usuario, { nombre: string; email: string; especialidad?: string; passwordActual?: string }>({
      query: (body) => ({ url: "/perfil", method: "PUT", body }),
    }),
    recuperarPassword: b.mutation<{ mensaje: string }, { email: string }>({
      query: (body) => ({ url: "/usuarios/recuperar-password", method: "POST", body }),
    }),
    recuperarDesdePerfil: b.mutation<{ mensaje: string }, void>({
      query: () => ({ url: "/perfil/recuperar-password", method: "POST" }),
    }),

    // ---------- pagos ----------
    // Idempotency-Key: si el pedido se repite (doble clic, corte de red), no se cobra dos veces.
    pagar: b.mutation<Pago, { turnoId: number; numero: string; vencimiento: string; cvv: string; titular: string; clave: string }>({
      query: ({ clave, ...body }) => ({ url: "/pagos", method: "POST", body, headers: { "Idempotency-Key": clave } }),
      invalidatesTags: ["Turnos", "Notificaciones"],
    }),

    crearProfesional: b.mutation<ProfesionalCreado, { nombre: string; email: string; passwordTemporal: string; especialidad: string }>({
      query: (body) => ({ url: "/admin/profesionales", method: "POST", body }),
      invalidatesTags: ["Usuarios"],
    }),
    cambiarEstadoUsuario: b.mutation<void, { id: number; activo: boolean }>({
      query: ({ id, activo }) => ({ url: `/admin/usuarios/${id}/activo`, method: "POST", body: { activo } }),
      invalidatesTags: ["Usuarios"],
    }),
    resetearPassword: b.mutation<ResetPassword, number>({
      query: (id) => ({ url: `/admin/usuarios/${id}/reset-password`, method: "POST" }),
      invalidatesTags: ["Usuarios"],
    }),
    facturacion: b.query<Facturacion, void>({
      query: () => "/admin/facturacion",
      providesTags: ["Facturacion"],
    }),
  }),
});

export const {
  useLoginMutation,
  useRegistrarMutation,
  useCambiarPasswordMutation,
  useTurnosDePacienteQuery,
  useTurnosDeProfesionalQuery,
  usePublicarHorarioMutation,
  useReprogramarTurnoMutation,
  useCancelarTurnoMutation,
  useLazySalaQuery,
  useProfesionalesQuery,
  useLazyEstadoAsistenteQuery,
  useElegirProfesionalMutation,
  useReservarMutation,
  useConfirmarMutation,
  useAbandonarReservaMutation,
  useHistoriaQuery,
  useFichaQuery,
  useRegistrarDiagnosticoMutation,
  useRegistrarRecetaMutation,
  useNotificacionesQuery,
  useMarcarLeidaMutation,
  useMarcarTodasLeidasMutation,
  useObrasSocialesQuery,
  useGuardarCoberturaMutation,
  useQuitarCoberturaMutation,
  useUsuariosQuery,
  useCrearProfesionalMutation,
  useCambiarEstadoUsuarioMutation,
  useResetearPasswordMutation,
  useFacturacionQuery,
  useEspecialidadesQuery,
  useActualizarPerfilMutation,
  useRecuperarPasswordMutation,
  useRecuperarDesdePerfilMutation,
  usePagarMutation,
} = mediconectaApi;
