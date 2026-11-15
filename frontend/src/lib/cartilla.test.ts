import { atiende, resumenCartilla } from "./cartilla";

describe("cartilla", () => {
  it("sin cartilla cargada atiende todas", () => {
    expect(atiende(null, "OSDE")).toBe(true);
    expect(atiende(undefined, "PAMI")).toBe(true);
  });

  it("con cartilla atiende solo las de la lista", () => {
    expect(atiende(["OSDE", "SWISS"], "OSDE")).toBe(true);
    expect(atiende(["OSDE", "SWISS"], "osde")).toBe(true);
    expect(atiende(["OSDE", "SWISS"], "PAMI")).toBe(false);
    expect(atiende(["OSDE"], null)).toBe(false);
  });

  it("una cartilla vacía es solo particular", () => {
    expect(atiende([], "OSDE")).toBe(false);
  });

  it("resume la cartilla para mostrarla", () => {
    const nombres: Record<string, string> = { OSDE: "OSDE", SWISS: "Swiss Medical" };
    expect(resumenCartilla(null)).toBe("Todas las obras sociales");
    expect(resumenCartilla([])).toBe("Solo particular");
    expect(resumenCartilla(["OSDE", "SWISS"], (c) => nombres[c] ?? c)).toBe("OSDE, Swiss Medical");
  });
});
