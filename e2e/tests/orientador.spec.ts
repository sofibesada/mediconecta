import { expect, test } from "@playwright/test";
import { abrirConSesion, irA, nuevoPaciente } from "./datos";

test.describe("HU-15 Orientador de especialidad (IA)", () => {
  test("E2E-18 el paciente cuenta qué le pasa, recibe una especialidad y filtra por ella; una urgencia indica la guardia", async ({
    page,
    request,
  }) => {
    const pac = await nuevoPaciente(request, "Paciente Orientador");
    await abrirConSesion(page, request, pac);
    await irA(page, "Agendar turno");

    await page.locator("#iaSintomas").fill("Me pica la piel y tengo ronchas desde hace una semana");
    await page.locator("#iaOrientar").click();
    await expect(page.locator("#iaEspecialidad")).toHaveText("Dermatología");
    await expect(page.locator("#iaResultado")).toContainText("no es un diagnóstico");

    await page.locator("#iaUsarEspecialidad").click();
    await expect(page.locator("#agEspecialidad")).toHaveValue("Dermatología");

    await page.locator("#iaSintomas").fill("Me duele el pecho y me falta el aire");
    await page.locator("#iaOrientar").click();
    await expect(page.locator("#iaResultado")).toContainText("107");
    await expect(page.locator("#iaUsarEspecialidad")).toHaveCount(0);
  });
});
