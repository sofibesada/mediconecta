import { expect, test } from "@playwright/test";
import { emailUnico, ingresarPorUI, nuevoPaciente, toast } from "./datos";

test.describe("HU-01 Registro e ingreso", () => {
  test("E2E-01 un paciente nuevo se registra y queda con la sesión iniciada", async ({ page }) => {
    await page.goto("/");
    await page.locator("#tabRegistro").click();
    await page.locator("#form-registro").getByLabel("Nombre").fill("Lucía Pérez");
    await page.locator("#form-registro").getByLabel("Email").fill(emailUnico("reg"));
    await page.locator("#form-registro").getByLabel("Contraseña").fill("clave1234");
    await page.locator("#btnRegistro").click();

    await expect(page.locator("#dashSaludo")).toHaveText("Hola, Lucía Pérez");
    const menu = page.locator("#navPrincipal");
    await expect(menu.getByText("Agendar turno", { exact: true })).toBeVisible();
    await expect(menu.getByText("Mis Citas", { exact: true })).toBeVisible();
    await expect(menu.getByText("Administración", { exact: true })).toBeHidden();
  });

  test("E2E-02 con una contraseña incorrecta no se ingresa", async ({ page, request }) => {
    const pac = await nuevoPaciente(request);

    await ingresarPorUI(page, pac.email, "otra-clave");

    await expect(toast(page, "err", "Email o contraseña incorrectos")).toBeVisible();
    await expect(page.locator("#login")).toHaveClass(/activa/);
    await expect(page.locator("#navPrincipal")).toBeHidden();
  });
});

test.describe("HU-02 Alta de profesionales", () => {
  test("E2E-03 el profesional creado por el admin cambia la contraseña temporal al ingresar", async ({ page }) => {
    const email = emailUnico("alta");
    await ingresarPorUI(page, process.env.ADMIN_EMAIL ?? "admin@mediconecta.com", process.env.ADMIN_PASSWORD ?? "admin1234");
    await expect(page.locator("#dashSaludo")).toContainText("Hola");
    await page.locator("#navPrincipal").getByText("Administración", { exact: true }).click();
    await page.getByLabel("Nombre").fill("Dr. Alta E2E");
    await page.getByLabel("Email").fill(email);
    await page.getByLabel("Contraseña temporal").fill("Temporal2026");
    await page.locator("#btnCrearProf").click();
    await expect(toast(page, "ok", /profesional/i)).toBeVisible();
    await page.locator("#navSalir").getByText("Cerrar Sesión").click();

    await ingresarPorUI(page, email, "Temporal2026");
    await expect(page.locator("#cambiarPassword")).toHaveClass(/activa/);
    await expect(page.locator("#navPrincipal")).toBeHidden();
    await page.getByLabel("Contraseña actual (temporal)").fill("Temporal2026");
    await page.getByLabel("Nueva contraseña", { exact: true }).fill("MiClave2026");
    await page.getByLabel("Repetir nueva contraseña").fill("MiClave2026");
    await page.locator("#btnCambiarPass").click();

    await expect(toast(page, "ok", "Contraseña actualizada")).toBeVisible();
    await expect(page.locator("#navPrincipal").getByText("Mi Agenda", { exact: true })).toBeVisible();
  });
});
