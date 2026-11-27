"""Resumen del mutation testing (PIT) para la pagina de la corrida de CI.

    python3 scripts/ci/mutacion-resumen.py target/pit-reports/mutations.xml >> "$GITHUB_STEP_SUMMARY"
"""
import collections
import sys
import xml.etree.ElementTree as ET

DETECTADOS = {"KILLED", "TIMED_OUT", "MEMORY_ERROR", "RUN_ERROR"}

raiz = ET.parse(sys.argv[1]).getroot()
total = collections.Counter()
por_paquete = collections.defaultdict(collections.Counter)
for m in raiz.iter("mutation"):
    estado = m.get("status")
    paquete = m.find("mutatedClass").text.rsplit(".", 1)[0].replace("ar.com.mediconecta.", "")
    total[estado] += 1
    por_paquete[paquete][estado] += 1


def puntaje(c):
    n = sum(c.values())
    return 100.0 * sum(c[e] for e in DETECTADOS) / n if n else 0.0


n = sum(total.values())
print("### Mutation testing (PIT) sobre la capa de dominio\n")
print(f"**Mutation score: {puntaje(total):.1f} %** ({sum(total[e] for e in DETECTADOS)} de {n} mutantes detectados). "
      "El build falla si baja de 60 %.\n")
print(f"Sobrevivieron {total['SURVIVED']}; sin cobertura {total['NO_COVERAGE']}.\n")
print("| Paquete | Mutantes | Detectados | Score |")
print("|---|---:|---:|---:|")
for paquete in sorted(por_paquete):
    c = por_paquete[paquete]
    print(f"| {paquete} | {sum(c.values())} | {sum(c[e] for e in DETECTADOS)} | {puntaje(c):.1f} % |")
print("\nReporte completo: artefacto mutacion-pit (abrir index.html).")
