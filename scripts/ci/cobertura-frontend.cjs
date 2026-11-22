// Resumen de cobertura del frontend para la pagina de la corrida de CI.
//   node scripts/ci/cobertura-frontend.cjs frontend/coverage/coverage-summary.json >> "$GITHUB_STEP_SUMMARY"
const fs = require("fs");

const total = JSON.parse(fs.readFileSync(process.argv[2], "utf8")).total;
const pct = (k) => `${total[k].pct.toFixed(1)} %`;

console.log("### Cobertura del frontend (pruebas unitarias, Vitest)\n");
console.log("| Lineas | Ramas | Funciones |");
console.log("|---:|---:|---:|");
console.log(`| ${pct("lines")} | ${pct("branches")} | ${pct("functions")} |\n`);
console.log("Las pantallas se prueban sobre todo con E2E (Playwright), que no suman a este numero.");
console.log("Reporte completo: artefacto cobertura-frontend (abrir index.html).");
