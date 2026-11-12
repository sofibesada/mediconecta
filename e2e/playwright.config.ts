import { defineConfig, devices } from "@playwright/test";

/**
 * E2E contra el sistema completo (docker compose up): frontend en :5173 y API en :8080.
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
    baseURL: process.env.FRONT_URL ?? "http://localhost:5173",
    // La API corre en UTC (contenedor): el navegador tambien, asi las fechas coinciden.
    timezoneId: "UTC",
    locale: "es-AR",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
