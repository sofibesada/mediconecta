import { errorDeTarjeta, formatear, formatearVencimiento, largoCvv, luhn, marca, TARJETAS_DE_PRUEBA, vigente } from "./tarjetas";

describe("tarjetas", () => {
  it("detecta la marca por los primeros dígitos", () => {
    expect(marca("4111")).toBe("Visa");
    expect(marca("5555 5555")).toBe("Mastercard");
    expect(marca("2221 00")).toBe("Mastercard");
    expect(marca("3782")).toBe("American Express");
    expect(marca("3056")).toBe("Diners Club");
    expect(marca("589562")).toBe("Naranja");
    expect(marca("6042 01")).toBe("Cabal");
    expect(marca("6759")).toBe("Maestro");
    expect(marca("9999")).toBeNull();
    expect(marca("")).toBeNull();
  });

  it("formatea en grupos según la marca y corta lo que sobra", () => {
    expect(formatear("4111111111111111")).toBe("4111 1111 1111 1111");
    expect(formatear("378282246310005")).toBe("3782 822463 10005");
    expect(formatear("30569309025904")).toBe("3056 930902 5904");
    expect(formatear("5555555555554444999")).toBe("5555 5555 5555 4444");
    expect(formatear("41-11")).toBe("4111");
  });

  it("valida el dígito verificador de Luhn", () => {
    expect(luhn("4111 1111 1111 1111")).toBe(true);
    expect(luhn("4111 1111 1111 1112")).toBe(false);
    expect(luhn("")).toBe(false);
  });

  it("todas las tarjetas de prueba tienen marca y dígito verificador válidos", () => {
    for (const t of TARJETAS_DE_PRUEBA) {
      expect(marca(t.numero)).not.toBeNull();
      expect(luhn(t.numero)).toBe(true);
    }
  });

  it("vencimiento MM/AA válido hasta el último día del mes", () => {
    const hoy = new Date(2026, 10, 14); // noviembre de 2026
    expect(formatearVencimiento("1128")).toBe("11/28");
    expect(formatearVencimiento("1")).toBe("1");
    expect(vigente("11/26", hoy)).toBe(true);
    expect(vigente("10/26", hoy)).toBe(false);
    expect(vigente("01/27", hoy)).toBe(true);
    expect(vigente("13/27", hoy)).toBe(false);
    expect(vigente("1127", hoy)).toBe(false);
  });

  it("American Express usa 4 dígitos de seguridad y las demás 3", () => {
    expect(largoCvv("American Express")).toBe(4);
    expect(largoCvv("Visa")).toBe(3);
  });

  it("devuelve el primer error de la tarjeta", () => {
    const hoy = new Date(2026, 10, 14);
    const ok = { numero: "4111 1111 1111 1111", vencimiento: "12/28", cvv: "123", titular: "Ana Pérez" };
    expect(errorDeTarjeta(ok, hoy)).toBeNull();
    expect(errorDeTarjeta({ ...ok, numero: "4111 1111 1111 1112" }, hoy)).toBe("El número de tarjeta no es válido");
    expect(errorDeTarjeta({ ...ok, numero: "4111 1111 111" }, hoy)).toBe("El número de tarjeta no es válido");
    expect(errorDeTarjeta({ ...ok, vencimiento: "01/20" }, hoy)).toContain("vencida");
    expect(errorDeTarjeta({ ...ok, cvv: "12" }, hoy)).toBe("El código de seguridad tiene 3 dígitos");
    expect(errorDeTarjeta({ ...ok, numero: "378282246310005", cvv: "123" }, hoy)).toBe("El código de seguridad tiene 4 dígitos");
    expect(errorDeTarjeta({ ...ok, titular: " " }, hoy)).toContain("titular");
  });
});
