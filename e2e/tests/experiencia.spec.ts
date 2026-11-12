import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { abrirConSesion, irA, nuevoPaciente, nuevoProfesional } from "./datos";

const ANCHOS = [
  { nombre: "celular", width: 360, height: 740 },
  { nombre: "tablet", width: 768, height: 1024 },
  { nombre: "escritorio", width: 1280, height: 800 },
];

test.describe("HU-11 Responsive, accesibilidad y temas", () => {
  for (const v of ANCHOS) {
    test(`E2E-09 sin desplazamiento horizontal en ${v.nombre} (${v.width}px)`, async ({ page, request }) => {
      await page.setViewportSize({ width: v.width, height: v.height });
      await page.goto("/");
      await expect(page.locator("#btnLogin")).toBeInViewport();
      await sinScrollHorizontal(page);

      const pac = await nuevoPaciente(request);
      await abrirConSesion(page, request, pac);
      for (const pantalla of ["Agendar turno", "Mis Citas", "Mi Perfil"]) {
        await irA(page, pantalla);
        await sinScrollHorizontal(page);
      }
    });
  }

  test("E2E-10 sin problemas de accesibilidad serios ni críticos (axe)", async ({ page, request }) => {
    await page.goto("/");
    await revisarAccesibilidad(page, "ingreso");

    const pac = await nuevoPaciente(request);
    await abrirConSesion(page, request, pac);
    await revisarAccesibilidad(page, "inicio del paciente");
    for (const pantalla of ["Agendar turno", "Mis Citas", "Mi Perfil"]) {
      await irA(page, pantalla);
      await page.waitForLoadState("networkidle");
      await revisarAccesibilidad(page, pantalla);
    }

    const prof = await nuevoProfesional(request);
    await page.context().clearCookies();
    await abrirConSesion(page, request, prof);
    await irA(page, "Mi Agenda");
    await revisarAccesibilidad(page, "agenda del profesional");
  });

  test("E2E-11 el tema oscuro se recuerda al volver a abrir", async ({ page }) => {
    await page.goto("/");
    await page.getByLabel("Tema de la aplicación").selectOption("oscuro");
    await expect(page.locator("html")).toHaveAttribute("data-tema", "oscuro");

    await page.reload();
    await expect(page.locator("html")).toHaveAttribute("data-tema", "oscuro");
    await expect(page.getByLabel("Tema de la aplicación")).toHaveValue("oscuro");
  });
});

async function sinScrollHorizontal(page: import("@playwright/test").Page) {
  const { scroll, cliente } = await page.evaluate(() => ({
    scroll: document.documentElement.scrollWidth,
    cliente: document.documentElement.clientWidth,
  }));
  expect(scroll, "el contenido es más ancho que la pantalla").toBeLessThanOrEqual(cliente);
}

async function revisarAccesibilidad(page: import("@playwright/test").Page, donde: string) {
  const r = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
  const graves = r.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
  const detalle = graves.map((v) => `${v.id} (${v.impact}): ${v.nodes.length} elemento(s) — ${v.help}`).join("\n");
  expect(graves, `Accesibilidad en ${donde}:\n${detalle}`).toEqual([]);
}
