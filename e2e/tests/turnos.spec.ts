import { expect, test } from "@playwright/test";
import { abrirConSesion, irA, nuevoPaciente, nuevoProfesional, publicarHorario, toast, turnoConfirmado } from "./datos";

test.describe("HU-03 Publicar disponibilidad", () => {
  test("E2E-04 el profesional publica un horario y lo ve en su agenda", async ({ page, request }) => {
    const prof = await nuevoProfesional(request);
    await abrirConSesion(page, request, prof);

    await irA(page, "Mi Agenda");
    await page.getByLabel("Fecha y hora").fill("2030-06-14T10:00");
    await page.getByLabel("Modalidad").selectOption("PRESENCIAL");
    await page.locator("#btnPublicar").click();

    await expect(toast(page, "ok", "Horario publicado")).toBeVisible();
    const fila = page.locator("#miAgendaLista .fila").filter({ hasText: "14 de junio de 2030" });
    await expect(fila).toHaveCount(1);
    await expect(fila.locator(".badge")).toHaveText("DISPONIBLE");
  });
});

test.describe("HU-04 Agendar y confirmar", () => {
  test("E2E-05 el paciente reserva y confirma un turno como particular", async ({ page, request }) => {
    const prof = await nuevoProfesional(request, "Dra. Reserva E2E");
    await publicarHorario(request, prof, 72);
    const pac = await nuevoPaciente(request, "Paciente Reserva");
    await abrirConSesion(page, request, pac);

    await irA(page, "Agendar turno");
    await page.getByLabel("Profesional").selectOption(String(prof.id)); // por id: el nombre se puede repetir entre corridas
    await page.locator("#agDispLista").getByRole("button", { name: "Reservar" }).first().click();
    await expect(page.locator("#agCardHold")).toBeVisible();
    await expect(page.locator("#agHoldContenido")).toContainText("La reserva vence en");
    await page.locator("#btnConfirmar").click();

    await expect(page.locator("#confirmacion")).toHaveClass(/activa/);
    await expect(page.locator("#confirmCard")).toContainText("Particular");
    await expect(page.locator("#confirmCard")).toContainText("CONFIRMADO");
    await expect(page.locator("#confirmCard")).toContainText("20.000");
    await page.getByRole("button", { name: "Ver mis citas" }).click();
    await expect(page.locator("#misCitasLista .fila").filter({ hasText: "Dra. Reserva E2E" }).locator(".badge")).toHaveText("CONFIRMADO");
  });

  test("E2E-06 si la obra social rechaza, el turno sigue reservado y se confirma como particular", async ({ page, request }) => {
    const prof = await nuevoProfesional(request, "Dr. Cobertura E2E");
    await publicarHorario(request, prof, 96);
    const pac = await nuevoPaciente(request, "Paciente Tope");
    const r = await request.put("http://localhost:8080/mediconecta/api/cobertura", {
      data: { obraSocial: "SWISS", numeroAfiliado: "12345677" },
      headers: { Authorization: "Bearer " + pac.token },
    });
    expect(r.ok(), await r.text()).toBeTruthy();
    await abrirConSesion(page, request, pac);

    await irA(page, "Agendar turno");
    await page.getByLabel("Profesional").selectOption(String(prof.id));
    await page.locator("#agDispLista").getByRole("button", { name: "Reservar" }).first().click();
    await page.locator("#btnConfirmar").click();

    await expect(page.locator("#agHoldContenido")).toContainText("No se pudo confirmar con tu obra social");
    await expect(page.locator("#agHoldContenido")).toContainText("La reserva vence en");
    await page.locator("#btnParticular").click();

    await expect(page.locator("#confirmacion")).toHaveClass(/activa/);
    await expect(page.locator("#confirmCard")).toContainText("Particular");
  });
});

test.describe("HU-05 Cancelar un turno", () => {
  test("E2E-07 con menos de 24 horas el turno no se puede cancelar", async ({ page, request }) => {
    const prof = await nuevoProfesional(request, "Dr. Cancelacion E2E");
    const pac = await nuevoPaciente(request);
    await turnoConfirmado(request, prof, pac, 5);
    await abrirConSesion(page, request, pac);

    await irA(page, "Mis Citas");
    const fila = page.locator("#misCitasLista .fila").filter({ hasText: "Dr. Cancelacion E2E" });
    page.once("dialog", (d) => d.accept());
    await fila.getByRole("button", { name: "Cancelar" }).click();

    await expect(toast(page, "err", "No se puede cancelar: faltan menos de 24hs para el turno")).toBeVisible();
    await expect(fila.locator(".badge")).toHaveText("CONFIRMADO");
  });
});

test.describe("HU-06 Cobertura", () => {
  test("E2E-08 se carga una cobertura activa y se rechaza un afiliado dado de baja", async ({ page, request }) => {
    const pac = await nuevoPaciente(request);
    await abrirConSesion(page, request, pac);
    await irA(page, "Mi Perfil");

    await page.getByLabel("Obra social o prepaga").selectOption("OSDE");
    await page.getByLabel("Número de afiliado").fill("12345679");
    await page.locator("#btnCobertura").click();
    await expect(toast(page, "err", "La obra social informó: Afiliado dado de baja")).toBeVisible();
    await expect(page.locator("#cobActual")).toContainText("No cargaste obra social");

    await page.getByLabel("Obra social o prepaga").selectOption("OSDE");
    await page.getByLabel("Número de afiliado").fill("12345678");
    await page.locator("#btnCobertura").click();
    await expect(toast(page, "ok", "Cobertura verificada")).toBeVisible();
    await expect(page.locator("#cobActual")).toContainText("12345678");
  });
});
