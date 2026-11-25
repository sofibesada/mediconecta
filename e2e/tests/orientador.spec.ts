import { expect, test } from "@playwright/test";
import { abrirConSesion, nuevoPaciente } from "./datos";

test.describe("HU-15 Asistente de salud (IA)", () => {
  test("E2E-18 el paciente le cuenta al asistente qué le pasa, recibe una especialidad y saca turno; una urgencia indica la guardia", async ({
    page,
    request,
  }) => {
    const pac = await nuevoPaciente(request, "Paciente Orientador");
    await abrirConSesion(page, request, pac);

    // El asistente está disponible desde el inicio, sin buscarlo en el menú.
    await page.locator("#asistAbrir").click();
    const charla = page.locator("#asistMensajes");
    await expect(charla).toContainText("Contame qué te pasa");

    await page.locator("#asistTexto").fill("Me pica la piel y tengo ronchas desde hace una semana");
    await page.locator("#asistEnviar").click();
    await expect(charla.locator(".asist-especialidad").last()).toHaveText("Dermatología");
    await expect(charla).toContainText("no es un diagnóstico");

    await charla.getByRole("button", { name: "Sacar turno de Dermatología" }).click();
    await expect(page).toHaveURL(/\/agendar$/);
    await expect(page.locator("#agEspecialidad")).toHaveValue("Dermatología");

    // La charla sigue al volver a abrirlo; una urgencia no ofrece turno.
    await page.locator("#asistAbrir").click();
    await expect(charla).toContainText("Me pica la piel");
    await page.locator("#asistTexto").fill("Me duele el pecho y me falta el aire");
    await page.keyboard.press("Enter");
    await expect(charla.locator(".burbuja-urgencia")).toContainText("107");
    await expect(charla.getByRole("button", { name: /Sacar turno/ })).toHaveCount(1);
  });
});
