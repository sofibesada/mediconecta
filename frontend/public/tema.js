// Tema guardado en este navegador; se aplica antes de pintar para que no parpadee.
// Es un archivo aparte (no un <script> en el HTML) para que la CSP no tenga que permitir
// scripts inline.
try {
  var t = localStorage.getItem("mc_tema");
  if (t === "claro" || t === "oscuro") document.documentElement.dataset.tema = t;
} catch (e) {
  /* sin localStorage: tema estándar */
}
