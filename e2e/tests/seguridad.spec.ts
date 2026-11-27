import { expect, test } from "@playwright/test";
import { abrirConSesion, irA, nuevoPaciente } from "./datos";

test.describe("Seguridad en la pantalla", () => {
  test("E2E-19 un nombre con HTML o JavaScript se muestra como texto y no se ejecuta (XSS)", async ({ page, request }) => {
    const malicioso = "<img src=x onerror=window.__xss=1>";
    const pac = await nuevoPaciente(request, malicioso);

    let dialogos = 0;
    page.on("dialog", async (d) => {
      dialogos++;
      await d.dismiss();
    });
    await abrirConSesion(page, request, pac);

    // El nombre aparece literal en el saludo y en el encabezado, sin crear elementos.
    await expect(page.locator("#dashSaludo")).toContainText(malicioso);
    await irA(page, "Mi Perfil");
    await expect(page.locator("#perfNombre")).toHaveValue(malicioso);

    expect(await page.evaluate(() => (window as unknown as { __xss?: number }).__xss)).toBeUndefined();
    expect(await page.locator("img[src='x']").count()).toBe(0);
    expect(dialogos).toBe(0);
  });
});
