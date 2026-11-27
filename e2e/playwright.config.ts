import { defineConfig, devices } from "@playwright/test";

/**
 * E2E contra el sistema completo (docker compose up): la app en https://localhost:8443 (nginx) y
 * la API directa en :8080 para preparar datos.
 * Cada prueba crea sus propios usuarios y horarios, asi que se pueden repetir y no
 * dependen del orden. Sin reintentos: una prueba que falla a veces es un defecto.
 */
export default defineConfig({
  testDir: "./tests",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  retries: 0,
  workers: 2,
  reporter: [["list"], ["html", { open: "never", outputFolder: "reporte" }], ["junit", { outputFile: "resultados/junit.xml" }]],
  outputDir: "resultados/artefactos",
  use: {
    baseURL: process.env.FRONT_URL ?? "https://localhost:8443",
    // Certificado autofirmado del entorno local.
    ignoreHTTPSErrors: true,
    // La API corre en hora de Argentina (TZ del contenedor): el navegador tambien, como un
    // usuario real, asi las fechas coinciden.
    timezoneId: "America/Argentina/Buenos_Aires",
    locale: "es-AR",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
