import { expect, test } from "@playwright/test";
import { abrirConSesion, irA, nuevoPaciente, nuevoProfesional, publicarHorario, toast, turnoConfirmado } from "./datos";

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

    // Otro intento con otra tarjeta es otro pago (no repite la respuesta del rechazo).
    await form.getByLabel("Número de tarjeta").fill("5555555555554444");
    await expect(form.locator(".tarjeta-marca")).toHaveText("Mastercard");
    await form.getByRole("button", { name: /^Pagar/ }).click();

    await expect(toast(page, "ok", "Pago aprobado con Mastercard terminada en 4444")).toBeVisible();
    await expect(fila.locator(".pill-ok")).toHaveText("Pagado");
    await expect(detalle).toContainText("Pagado con Mastercard terminada en 4444");
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
    await page.getByLabel("Profesional").selectOption({ label: nombre });
    const lista = page.locator("#agDispLista");
    await expect(lista.getByRole("button", { name: "Reservar" })).toHaveCount(2);

    await page.locator("#agModalidad").selectOption("VIRTUAL");
    await expect(lista.getByRole("button", { name: "Reservar" })).toHaveCount(1);
    await expect(lista).toContainText("Teleconsulta");

    await page.locator("#agEspecialidad").selectOption("Pediatría");
    await expect(page.getByLabel("Profesional").locator("option", { hasText: nombre })).toHaveCount(0);
  });
});
