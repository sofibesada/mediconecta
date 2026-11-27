"""Resumen de un reporte JSON de OWASP ZAP para la pagina de la corrida de CI.

    python3 scripts/ci/zap-resumen.py target/zap/baseline.json "Baseline" >> "$GITHUB_STEP_SUMMARY"

Sale con codigo 1 si hay alguna alerta High (criterio de la catedra: 0 High). Las Medium se
listan para justificarlas una por una en el informe de seguridad.
"""
import json
import sys

RIESGO = {"3": "High", "2": "Medium", "1": "Low", "0": "Info"}

reporte = json.load(open(sys.argv[1], encoding="utf-8"))
titulo = sys.argv[2] if len(sys.argv) > 2 else "OWASP ZAP"
alertas = [a for sitio in reporte.get("site", []) for a in sitio.get("alerts", [])]
alertas.sort(key=lambda a: (-int(a["riskcode"]), a["name"]))

cuenta = {nivel: 0 for nivel in RIESGO.values()}
for a in alertas:
    cuenta[RIESGO[a["riskcode"]]] += 1

print(f"### OWASP ZAP: {titulo}\n")
print(" · ".join(f"**{n}: {c}**" if n in ("High", "Medium") else f"{n}: {c}" for n, c in cuenta.items()) + "\n")
if alertas:
    print("| Riesgo | Regla | Alerta | Instancias |")
    print("|---|---|---|---:|")
    for a in alertas:
        print(f"| {RIESGO[a['riskcode']]} | {a['pluginid']} | {a['name']} | {a.get('count', len(a.get('instances', [])))} |")
print()
if cuenta["High"]:
    print("**Hay alertas High: el job falla.**")
    sys.exit(1)
