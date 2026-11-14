import type { Turno } from "../api/tipos";
import { filtrarHorarios, resumenDePago } from "./turnos";

const turno = (extra: Partial<Turno>): Turno => ({
  id: 1, fechaHora: "2030-06-14T10:00:00", estado: "CONFIRMADO", modalidad: "PRESENCIAL", profesionalId: 2, ...extra,
});

describe("turnos", () => {
  it("filtra horarios por modalidad y desde un día", () => {
    const lista = [
      turno({ id: 1, fechaHora: "2030-06-14T10:00:00", modalidad: "PRESENCIAL" }),
      turno({ id: 2, fechaHora: "2030-06-15T10:00:00", modalidad: "VIRTUAL" }),
      turno({ id: 3, fechaHora: "2030-06-20T10:00:00", modalidad: "PRESENCIAL" }),
    ];
    expect(filtrarHorarios(lista, "", "").map((t) => t.id)).toEqual([1, 2, 3]);
    expect(filtrarHorarios(lista, "VIRTUAL", "").map((t) => t.id)).toEqual([2]);
    expect(filtrarHorarios(lista, "", "2030-06-15").map((t) => t.id)).toEqual([2, 3]);
    expect(filtrarHorarios(lista, "PRESENCIAL", "2030-06-15").map((t) => t.id)).toEqual([3]);
  });

  it("particular: paga el valor entero de la consulta", () => {
    const r = resumenDePago(turno({ importeCopago: 20000, importeObraSocial: null }));
    expect(r).toEqual({ valorConsulta: 20000, cubreObraSocial: 0, aPagar: 20000, pagado: false, puedePagar: true });
  });

  it("con obra social: paga solo el copago", () => {
    const r = resumenDePago(turno({ importeCopago: 6000, importeObraSocial: 14000 }));
    expect(r.valorConsulta).toBe(20000);
    expect(r.cubreObraSocial).toBe(14000);
    expect(r.aPagar).toBe(6000);
  });

  it("no se puede pagar si ya está pagado, si no hay importe o si no está confirmado", () => {
    expect(resumenDePago(turno({ importeCopago: 6000, pago: { marca: "Visa", ultimos4: "1111", pagadoEn: "2030-01-01T10:00:00" } })).puedePagar).toBe(false);
    expect(resumenDePago(turno({ importeCopago: 0 })).puedePagar).toBe(false);
    expect(resumenDePago(turno({ estado: "CANCELADO", importeCopago: 6000 })).puedePagar).toBe(false);
    expect(resumenDePago(turno({ estado: "DISPONIBLE" })).valorConsulta).toBeNull();
  });
});
