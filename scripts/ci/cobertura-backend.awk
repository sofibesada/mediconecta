# Resumen de cobertura del backend para la pagina de la corrida de CI.
#   awk -f scripts/ci/cobertura-backend.awk target/site/jacoco/jacoco.csv >> "$GITHUB_STEP_SUMMARY"
# Columnas de jacoco.csv: 6/7 ramas perdidas/cubiertas, 8/9 lineas perdidas/cubiertas.
BEGIN { FS = "," }
NR > 1 {
  split($2, p, "."); m = p[4]
  lm[m] += $8; lc[m] += $9; bm[m] += $6; bc[m] += $7
  LM += $8; LC += $9; BM += $6; BC += $7
  if ($2 ~ /\.business$/) { NLM += $8; NLC += $9; NBM += $6; NBC += $7 }
}
function pct(c, mi) { return (c + mi) ? sprintf("%.1f %%", 100 * c / (c + mi)) : "-" }
END {
  print "### Cobertura del backend (pruebas unitarias, JaCoCo)"
  print ""
  printf "**Total: %s de lineas, %s de ramas.**\n\n", pct(LC, LM), pct(BC, BM)
  printf "Capa de negocio (paquetes business): %s de lineas, %s de ramas. ", pct(NLC, NLM), pct(NBC, NBM)
  print "El build falla si baja de 80 % de lineas o 70 % de ramas."
  print ""
  print "| Modulo | Lineas | Ramas |"
  print "|---|---:|---:|"
  n = 0; for (m in lm) mods[++n] = m
  for (i = 2; i <= n; i++) { x = mods[i]; for (j = i - 1; j >= 1 && mods[j] > x; j--) mods[j + 1] = mods[j]; mods[j + 1] = x }
  for (i = 1; i <= n; i++) { m = mods[i]; printf "| %s | %s | %s |\n", m, pct(lc[m], lm[m]), pct(bc[m], bm[m]) }
  print ""
  print "Las pruebas de la API y E2E corren contra el sistema levantado y no suman a este numero."
  print "Reporte completo: artefacto cobertura-backend (abrir index.html)."
}
