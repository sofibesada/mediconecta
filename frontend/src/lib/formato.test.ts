import { conSegundos, cuentaRegresiva, esFutura, estadoLindo, fechaParaInput, fmtFecha, fmtPesos, iniciales, modalidadLinda, rolLindo } from "./formato";

describe("formato", () => {
  it("muestra los roles en castellano", () => {
    expect(rolLindo("ADMIN")).toBe("Administrador");
    expect(rolLindo("PACIENTE")).toBe("Paciente");
    expect(rolLindo("OTRO")).toBe("OTRO");
    expect(rolLindo(undefined)).toBe("");
  });

  it("arma las iniciales con hasta dos palabras", () => {
    expect(iniciales("lucía pérez gómez")).toBe("LP");
    expect(iniciales("  Ana ")).toBe("A");
    expect(iniciales("")).toBe("MC");
    expect(iniciales(null)).toBe("MC");
  });

  it("modalidad y estado", () => {
    expect(modalidadLinda("VIRTUAL")).toContain("Teleconsulta");
    expect(modalidadLinda("PRESENCIAL")).toBe("Presencial");
    expect(estadoLindo("RESERVADO_TEMPORAL")).toBe("RESERVADO TEMPORAL");
  });

  it("muestra el año solo si no es el actual", () => {
    const hoy = new Date(2030, 0, 1);
    expect(fmtFecha("2030-06-14T10:00:00", hoy)).not.toContain("2030");
    expect(fmtFecha("2031-06-14T10:00:00", hoy)).toContain("2031");
    expect(fmtFecha("2031-06-14T10:00:00", hoy)).toContain("junio");
    expect(fmtFecha(null)).toBe("—");
    expect(fmtFecha("no es una fecha")).toBe("no es una fecha");
  });

  it("formatea pesos argentinos", () => {
    expect(fmtPesos(20000)).toBe("$ 20.000");
    expect(fmtPesos(1234.5)).toBe("$ 1.234,5");
    expect(fmtPesos(null)).toBe("—");
  });

  it("fecha local para el input datetime-local, sin pasar a UTC", () => {
    expect(fechaParaInput(new Date(2030, 5, 4, 9, 5))).toBe("2030-06-04T09:05");
    expect(conSegundos("2030-06-04T09:05")).toBe("2030-06-04T09:05:00");
    expect(conSegundos("2030-06-04T09:05:30")).toBe("2030-06-04T09:05:30");
  });

  it("solo acepta fechas futuras", () => {
    const ahora = new Date(2030, 0, 1, 12, 0);
    expect(esFutura("2030-01-01T12:01", ahora)).toBe(true);
    expect(esFutura("2030-01-01T11:59", ahora)).toBe(false);
  });

  it("cuenta regresiva de la reserva", () => {
    const ahora = Date.parse("2030-01-01T12:00:00Z");
    expect(cuentaRegresiva("2030-01-01T12:04:05Z", ahora)).toEqual({ segundos: 245, texto: "04:05" });
    expect(cuentaRegresiva("2030-01-01T11:00:00Z", ahora)).toEqual({ segundos: 0, texto: "00:00" });
  });
});
