import { crearStore } from "../app/store";
import { sesionIniciada } from "../features/sesion/sesionSlice";
import { mediconectaApi } from "./mediconectaApi";
import { mensajeDeError, SIN_CONEXION } from "./baseQuery";

function respuesta(status: number, cuerpo?: unknown) {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status, headers: { "Content-Type": "application/json" } });
}

describe("baseQuery", () => {
  it("manda el token en cada pedido y devuelve los datos", async () => {
    const fetchMock = vi.fn().mockResolvedValue(respuesta(200, [{ id: 1, nombre: "Dra. X" }]));
    vi.stubGlobal("fetch", fetchMock);
    const store = crearStore();
    store.dispatch(sesionIniciada({ id: 1, nombre: "A", email: "a@x", rol: "PACIENTE", token: "abc" }));

    const r = await store.dispatch(mediconectaApi.endpoints.profesionales.initiate());

    expect(r.data).toEqual([{ id: 1, nombre: "Dra. X" }]);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/profesionales$/);
    expect(init.headers.Authorization).toBe("Bearer abc");
    expect(init.credentials).toBe("same-origin");
  });

  it("el asistente de agendamiento manda la cookie de sesión", async () => {
    const fetchMock = vi.fn().mockResolvedValue(respuesta(200, { asistenteId: 1, pedidosAtendidos: 1 }));
    vi.stubGlobal("fetch", fetchMock);
    const store = crearStore();
    await store.dispatch(mediconectaApi.endpoints.elegirProfesional.initiate(5));
    const [, init] = fetchMock.mock.calls[0];
    expect(init.credentials).toBe("include");
    expect(init.method).toBe("PUT");
    expect(JSON.parse(init.body)).toEqual({ profesionalId: 5 });
  });

  it("traduce el error de la API al mensaje que ve el usuario", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respuesta(409, { error: "No se puede cancelar: faltan menos de 24hs para el turno" })));
    const store = crearStore();
    const r = await store.dispatch(mediconectaApi.endpoints.cancelarTurno.initiate(3));
    expect("error" in r && r.error).toEqual({ status: 409, mensaje: "No se puede cancelar: faltan menos de 24hs para el turno" });
  });

  it("acepta errores en formato Problem Details", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respuesta(412, { title: "Precondition Failed", detail: "El turno cambió" })));
    const store = crearStore();
    const r = await store.dispatch(mediconectaApi.endpoints.cancelarTurno.initiate(3));
    expect("error" in r && r.error).toEqual({ status: 412, mensaje: "El turno cambió" });
  });

  it("sin conexión avisa que el servidor no responde", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));
    const store = crearStore();
    const r = await store.dispatch(mediconectaApi.endpoints.cancelarTurno.initiate(3));
    expect("error" in r && r.error).toEqual({ status: "SIN_CONEXION", mensaje: SIN_CONEXION });
  });

  it("con un 401 cierra la sesión y avisa", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respuesta(401)));
    const store = crearStore();
    store.dispatch(sesionIniciada({ id: 1, nombre: "A", email: "a@x", rol: "PACIENTE", token: "vencido" }));

    await store.dispatch(mediconectaApi.endpoints.notificaciones.initiate());

    expect(store.getState().sesion.usuario).toBeNull();
    expect(store.getState().ui.toasts[0]).toMatchObject({ tipo: "err", mensaje: "Tu sesión venció. Volvé a iniciar sesión." });
  });

  it("un 401 en el login (sin token) no es una sesión vencida", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respuesta(401, { error: "Email o contraseña incorrectos" })));
    const store = crearStore();
    const r = await store.dispatch(mediconectaApi.endpoints.login.initiate({ email: "a@x", password: "mal" }));
    expect("error" in r && r.error).toEqual({ status: 401, mensaje: "Email o contraseña incorrectos" });
    expect(store.getState().ui.toasts).toHaveLength(0);
  });

  it("mensajeDeError entiende errores de la API y de JavaScript", () => {
    expect(mensajeDeError({ status: 400, mensaje: "Dato inválido" })).toBe("Dato inválido");
    expect(mensajeDeError(new Error("boom"))).toBe("boom");
    expect(mensajeDeError(42)).toBe("Ocurrió un error inesperado");
  });
});
