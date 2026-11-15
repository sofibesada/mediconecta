import { expect, test } from "@playwright/test";
import { abrirConSesion, ADMIN_EMAIL, ADMIN_PASSWORD, API, irA, login, nuevoPaciente, nuevoProfesional, publicarHorario, toast, turnoConfirmado } from "./datos";

test.describe("HU-12 Pagar el turno online", () => {
  test("E2E-12 el paciente ve cuánto debe, una tarjeta rechazada no cobra y otra aprobada deja el turno pagado", async ({ page, request }) => {
    const prof = await nuevoProfesional(request, "Dra. Pago E2E", "Cardiología");
    const pac = await nuevoPaciente(request, "Paciente Pago");
    await turnoConfirmado(request, prof, pac, 120);
    await abrirConSesion(page, request, pac);

    await irA(page, "Mis Citas");
    const fila = page.locator("#misCitasLista .fila").filter({ hasText: "Dra. Pago E2E" });
    await expect(fila.locator(".pill-pendiente")).toContainText("A pagar");
    await fila.getByRole("button", { name: "Ver detalle" }).click();
    const detalle = fila.locator(".detalle-cita");
    await expect(detalle).toContainText("Cardiología");
    await expect(detalle).toContainText("20.000");

    await detalle.locator("[id^=btnPagar-]").click();
    const form = fila.locator(".tarjeta-form");
    await form.getByLabel("Número de tarjeta").fill("4000000000000002");
    await expect(form.locator(".tarjeta-marca")).toHaveText("Visa");
    await form.getByLabel("Vencimiento").fill("12/30");
    await form.getByLabel("Código de seguridad").fill("123");
    await form.getByLabel(/Titular/).fill("PACIENTE PAGO");
    await form.getByRole("button", { name: /^Pagar/ }).click();
    await expect(form).toContainText("rechazada por el banco emisor");

    // Otro intento con otra tarjeta es otro pago (no repite la respuesta del rechazo). El botón
    // completa una Visa de prueba que la pasarela aprueba.
    await form.getByRole("button", { name: "Usar una tarjeta de prueba" }).click();
    await expect(form.locator(".tarjeta-marca")).toHaveText("Visa");
    await form.getByRole("button", { name: /^Pagar/ }).click();

    await expect(toast(page, "ok", "Pago aprobado con Visa terminada en 1111")).toBeVisible();
    await expect(fila.locator(".pill-ok")).toHaveText("Pagado");
    await expect(detalle).toContainText("Pagado con Visa terminada en 1111");
  });
});

test.describe("HU-04 Agendar y confirmar", () => {
  test("E2E-13 los filtros por especialidad y modalidad muestran solo lo que corresponde", async ({ page, request }) => {
    const nombre = "Dra. Filtro " + Date.now();
    const prof = await nuevoProfesional(request, nombre, "Dermatología");
    await publicarHorario(request, prof, 100, "PRESENCIAL");
    await publicarHorario(request, prof, 101, "VIRTUAL");
    const pac = await nuevoPaciente(request, "Paciente Filtros");
    await abrirConSesion(page, request, pac);

    await irA(page, "Agendar turno");
    await page.locator("#agEspecialidad").selectOption("Dermatología");
    await page.getByLabel("Profesional").selectOption(String(prof.id));
    const lista = page.locator("#agDispLista");
    await expect(lista.getByRole("button", { name: "Reservar" })).toHaveCount(2);

    await page.locator("#agModalidad").selectOption("VIRTUAL");
    await expect(lista.getByRole("button", { name: "Reservar" })).toHaveCount(1);
    await expect(lista).toContainText("Teleconsulta");

    await page.locator("#agEspecialidad").selectOption("Pediatría");
    await expect(page.getByLabel("Profesional").locator("option", { hasText: nombre })).toHaveCount(0);
  });
});

test.describe("HU-12 Pagar el turno online (lo ven el profesional y el administrador)", () => {
  test("E2E-16 el profesional ve qué turnos están pagados y cuánto cobrar en el consultorio", async ({ page, request }) => {
    const prof = await nuevoProfesional(request, "Dra. Cobros " + Date.now());
    const pac = await nuevoPaciente(request, "Paciente Cobros");
    const pagado = await turnoConfirmado(request, prof, pac, 140);
    await turnoConfirmado(request, prof, pac, 150);
    const r = await request.post(API + "/pagos", {
      data: { turnoId: pagado.id, numero: "4111111111111111", vencimiento: "12/30", cvv: "123", titular: "PACIENTE COBROS" },
      headers: { Authorization: "Bearer " + pac.token, "Idempotency-Key": "e2e16-" + Date.now() },
    });
    expect(r.status(), await r.text()).toBe(201);

    await abrirConSesion(page, request, prof);
    await irA(page, "Mi Agenda");
    const filas = page.locator("#miAgendaLista .fila").filter({ hasText: "Paciente Cobros" });
    await expect(filas.locator(".pill-ok")).toHaveText(["Pagado online"]);
    await expect(filas.locator(".pill-pendiente")).toContainText(["Cobrar en el consultorio"]);
  });

  test("E2E-17 el administrador ve los pagos con el nombre del paciente", async ({ page, request }) => {
    const prof = await nuevoProfesional(request, "Dr. Facturado " + Date.now());
    const nombre = "Paciente Facturado " + Date.now();
    const pac = await nuevoPaciente(request, nombre);
    const t = await turnoConfirmado(request, prof, pac, 160);
    const r = await request.post(API + "/pagos", {
      data: { turnoId: t.id, numero: "5555555555554444", vencimiento: "12/30", cvv: "123", titular: "PACIENTE" },
      headers: { Authorization: "Bearer " + pac.token, "Idempotency-Key": "e2e17-" + Date.now() },
    });
    expect(r.status(), await r.text()).toBe(201);

    const admin = await login(request, ADMIN_EMAIL, ADMIN_PASSWORD);
    await abrirConSesion(page, request, { ...admin, password: ADMIN_PASSWORD });
    await irA(page, "Facturación");
    await expect(page.locator("#factPagos .fila").filter({ hasText: nombre })).toContainText("Mastercard terminada en 4444");
  });
});
