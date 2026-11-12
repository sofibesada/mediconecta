// Lighthouse (accesibilidad) sobre el frontend levantado. Falla si el puntaje es menor a 90.
// Usa el Chromium que instala Playwright, asi no depende de tener Chrome en la maquina.
import { chromium } from "@playwright/test";
import * as chromeLauncher from "chrome-launcher";
import lighthouse from "lighthouse";
import { mkdirSync, writeFileSync } from "node:fs";

const URL = process.env.FRONT_URL ?? "http://localhost:5173";
const MINIMO = 90;

const chrome = await chromeLauncher.launch({
  chromePath: chromium.executablePath(),
  chromeFlags: ["--headless=new", "--no-sandbox"],
});
try {
  const r = await lighthouse(URL, {
    port: chrome.port,
    onlyCategories: ["accessibility", "best-practices"],
    output: "html",
    logLevel: "error",
  });
  mkdirSync("resultados", { recursive: true });
  writeFileSync("resultados/lighthouse.html", r.report);

  const accesibilidad = Math.round(r.lhr.categories.accessibility.score * 100);
  const practicas = Math.round(r.lhr.categories["best-practices"].score * 100);
  console.log(`Lighthouse en ${URL}: accesibilidad ${accesibilidad} / buenas practicas ${practicas}`);
  for (const a of Object.values(r.lhr.audits)) {
    if (a.score !== null && a.score < 1 && r.lhr.categories.accessibility.auditRefs.some((ref) => ref.id === a.id && ref.weight > 0)) {
      console.log(`  - ${a.id}: ${a.title}`);
    }
  }
  if (accesibilidad < MINIMO) {
    console.error(`La accesibilidad (${accesibilidad}) esta por debajo de ${MINIMO}`);
    process.exitCode = 1;
  }
} finally {
  await chrome.kill();
}
