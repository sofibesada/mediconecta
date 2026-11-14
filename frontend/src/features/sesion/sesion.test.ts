import type { Usuario } from "../../api/tipos";
import { crearStore, crearStoreDeLaApp } from "../../app/store";
import { turnoRetenido } from "../agenda/agendaSlice";
import { temaElegido } from "../ui/uiSlice";
import reducer, { CLAVE_SESION, leerSesionGuardada, sesionCerrada, sesionExpirada, sesionIniciada, tokenRenovado, usuarioActualizado } from "./sesionSlice";
import { hayQueRenovar, INACTIVIDAD_MAXIMA_MS, vencimientoDelToken } from "./token";

const paciente: Usuario = { id: 7, nombre: "Ana", email: "ana@x.com", rol: "PACIENTE", token: "t1" };

/** JWT de prueba (sin firma válida: solo importa el claim exp). */
function jwt(expSegundos: number) {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
  return `${b64({ alg: "HS256" })}.${b64({ sub: "7", exp: expSegundos })}.firma`;
}

describe("sesionSlice", () => {
  it("inicia, renueva el token y cierra la sesión", () => {
    let s = reducer(undefined, sesionIniciada(paciente));
    expect(s.usuario?.nombre).toBe("Ana");
    s = reducer(s, tokenRenovado("t2"));
    expect(s.usuario?.token).toBe("t2");
    expect(reducer(s, sesionCerrada()).usuario).toBeNull();
    expect(reducer(s, sesionExpirada("venció")).usuario).toBeNull();
  });

  it("al actualizar la cobertura conserva el token y borra los campos que ya no vienen", () => {
    let s = reducer(undefined, sesionIniciada({ ...paciente, obraSocial: "OSDE", planObraSocial: "Plata", copagoPorcentaje: 20 }));
    s = reducer(s, usuarioActualizado({ id: 7, nombre: "Ana", email: "ana@x.com", rol: "PACIENTE" }));
    expect(s.usuario?.token).toBe("t1");
    expect(s.usuario).not.toHaveProperty("obraSocial");
    expect(s.usuario).not.toHaveProperty("copagoPorcentaje");
  });

  it("descarta una sesión guardada sin token o con JSON roto", () => {
    localStorage.setItem(CLAVE_SESION, JSON.stringify({ ...paciente, token: undefined }));
    expect(leerSesionGuardada().usuario).toBeNull();
    localStorage.setItem(CLAVE_SESION, "{roto");
    expect(leerSesionGuardada().usuario).toBeNull();
    localStorage.setItem(CLAVE_SESION, JSON.stringify(paciente));
    expect(leerSesionGuardada().usuario?.id).toBe(7);
  });
});

describe("store de la aplicación", () => {
  it("guarda la sesión y el tema en el localStorage y los borra al salir", () => {
    const store = crearStoreDeLaApp();
    store.dispatch(sesionIniciada(paciente));
    store.dispatch(temaElegido("oscuro"));
    expect(JSON.parse(localStorage.getItem(CLAVE_SESION)!).id).toBe(7);
    expect(localStorage.getItem("mc_tema")).toBe("oscuro");

    store.dispatch(sesionCerrada());
    expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
  });

  it("al cerrar la sesión olvida la reserva en curso", () => {
    const store = crearStore();
    store.dispatch(sesionIniciada(paciente));
    store.dispatch(turnoRetenido({ id: 1, fechaHora: "2030-01-01T10:00:00", estado: "RESERVADO_TEMPORAL", modalidad: "PRESENCIAL", profesionalId: 2 }));
    expect(store.getState().agenda.turnoRetenido?.id).toBe(1);
    store.dispatch(sesionExpirada("venció"));
    expect(store.getState().agenda.turnoRetenido).toBeNull();
  });
});

describe("renovación del token", () => {
  const ahora = Date.parse("2030-01-01T12:00:00Z");

  it("lee el vencimiento del JWT", () => {
    expect(vencimientoDelToken(jwt(ahora / 1000 + 60))).toBe(ahora + 60_000);
    expect(vencimientoDelToken("no-es-un-jwt")).toBe(0);
  });

  it("renueva solo si faltan menos de 5 minutos y el usuario estuvo activo", () => {
    expect(hayQueRenovar(jwt(ahora / 1000 + 10 * 60), ahora, ahora)).toBe(false); // le quedan 10 min
    expect(hayQueRenovar(jwt(ahora / 1000 + 4 * 60), ahora, ahora)).toBe(true); // le quedan 4 min
    expect(hayQueRenovar(jwt(ahora / 1000 + 4 * 60), ahora, ahora - INACTIVIDAD_MAXIMA_MS - 1)).toBe(false); // inactivo
  });
});
