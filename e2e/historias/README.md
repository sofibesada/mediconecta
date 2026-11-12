# Historias de usuario

Una historia por archivo, en Gherkin (en español). Cada una indica su prioridad MoSCoW
con una etiqueta (`@must`, `@should`, `@could`).

| HU | Historia | Prioridad |
|---|---|---|
| HU-01 | Registro e ingreso de pacientes | Must |
| HU-02 | Alta de profesionales por el administrador | Must |
| HU-03 | Publicación de horarios disponibles | Must |
| HU-04 | Agendar y confirmar un turno | Must |
| HU-05 | Cancelar un turno | Must |
| HU-06 | Cargar la cobertura de la obra social | Should |
| HU-07 | Avisos de turnos | Should |
| HU-08 | Teleconsulta | Should |
| HU-09 | Historia clínica | Should |
| HU-10 | Reprogramar un turno | Could |
| HU-11 | Uso en celular, tablet y escritorio, con accesibilidad y temas | Could |
| — | Pago online de la consulta | Won't (fuera del alcance de esta versión) |
| — | Aplicación móvil nativa | Won't (fuera del alcance de esta versión) |

Los escenarios con etiqueta `@E2E-NN` están automatizados en `e2e/tests`: cada prueba
lleva el mismo código en el título. Los marcados `@falla-externa` describen qué ve el
usuario cuando falla un sistema externo (obra social, videollamadas o broker).
