import { expect, test } from "@playwright/test";
import { abrirConSesion, API, irA, nuevoPaciente, nuevoProfesional, publicarHorario, toast } from "./datos";

test.describe("HU-14 Cartilla de obras sociales", () => {
  test("E2E-14 si el profesional no atiende la obra social del paciente, el turno se confirma como particular", async ({ page, request }) => {
    const prof = await nuevoProfesional(request, "Dra. Cartilla " + Date.now(), "Pediatría");
    const r = await request.put(API + "/perfil/cartilla", {
      data: { todas: false, obrasSociales: ["SWISS"] },
      headers: { Authorization: "Bearer " + prof.token },
    });
    expect(r.ok(), await r.text()).toBeTruthy();
    await publicarHorario(request, prof, 130);
    const pac = await nuevoPaciente(request, "Paciente Cartilla");
    const c = await request.put(API + "/cobertura", {
      data: { obraSocial: "OSDE", numeroAfiliado: "12345678" },
      headers: { Authorization: "Bearer " + pac.token },
    });
    expect(c.ok(), await c.text()).toBeTruthy();
    await abrirConSesion(page, request, pac);

    await irA(page, "Agendar turno");
    await page.getByLabel("Profesional").selectOption(String(prof.id));
    await expect(page.locator("#agCartilla")).toContainText("No atiende OSDE");

    // Con el filtro, el profesional desaparece de la lista.
    await page.locator("#agSoloMiObraSocial").check();
    await expect(page.getByLabel("Profesional").locator(`option[value="${prof.id}"]`)).toHaveCount(0);
    await page.locator("#agSoloMiObraSocial").uncheck();

    await page.getByLabel("Profesional").selectOption(String(prof.id));
    await page.locator("#agDispLista").getByRole("button", { name: "Reservar" }).first().click();
    await expect(page.locator("#agFueraDeCartilla")).toBeVisible();
    await expect(page.locator("#btnConfirmar")).toHaveText("Confirmar como particular");
    await page.locator("#btnConfirmar").click();

    await expect(page.locator("#confirmacion")).toHaveClass(/activa/);
    await expect(page.locator("#confirmCard")).toContainText("Particular");
  });

  test("E2E-15 el profesional elige qué obras sociales atiende", async ({ page, request }) => {
    const prof = await nuevoProfesional(request, "Dr. Convenios " + Date.now());
    await abrirConSesion(page, request, prof);

    await irA(page, "Mi Perfil");
    await expect(page.locator("#cartTodas")).toBeChecked();
    await page.locator("#cartAlgunas").check();
    await page.locator("#cart-OSDE").check();
    await page.locator("#cart-PAMI").check();
    await page.locator("#btnCartilla").click();
    await expect(toast(page, "ok", "Cartilla actualizada")).toBeVisible();

    const perfil = await request.get(API + "/perfil", { headers: { Authorization: "Bearer " + prof.token } });
    expect((await perfil.json()).cartillaObrasSociales).toEqual(["OSDE", "PAMI"]);
  });
});
