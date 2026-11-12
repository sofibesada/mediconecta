import { useState, type FormEvent } from "react";
import { useAccion, useRevisarNotificacionesPronto, useToast, useUsuario } from "../app/hooks";
import { usePublicarHorarioMutation, useReprogramarTurnoMutation, useTurnosDeProfesionalQuery } from "../api/mediconectaApi";
import type { Modalidad, Turno } from "../api/tipos";
import { Actualizar, BotonAccion, ContenidoDeLista, Encabezado, Lista, Pantalla } from "../components/comunes";
import { FilaTurno } from "../components/FilaTurno";
import { useVideollamada } from "../components/useVideollamada";
import { conSegundos, esFutura, fechaParaInput } from "../lib/formato";

/** Por defecto se propone un horario en 3 días, en punto. */
function fechaPropuesta(): string {
  const d = new Date(Date.now() + 3 * 864e5);
  d.setMinutes(0, 0, 0);
  return fechaParaInput(d);
}

export function MiAgenda() {
  const usuario = useUsuario();
  const unirse = useVideollamada();
  const turnos = useTurnosDeProfesionalQuery(usuario.id);
  const [reprogramando, setReprogramando] = useState<number | null>(null);

  return (
    <Pantalla id="miAgenda">
      <Encabezado titulo="Mi Agenda" subtitulo="Publicá tu disponibilidad y mirá tus turnos" />
      <PublicarHorario />
      <Actualizar onClick={() => void turnos.refetch()} />
      <Lista titulo="Todos mis turnos" id="miAgendaLista">
        <ContenidoDeLista cargando={turnos.isLoading} error={turnos.error} datos={turnos.data} vacio="Todavía no publicaste horarios.">
          {(lista) =>
            lista.map((t) => (
              <FilaTurno
                key={t.id}
                turno={t}
                vistaProfesional
                onUnirse={unirse}
                onReprogramar={() => setReprogramando(reprogramando === t.id ? null : t.id)}
              >
                {reprogramando === t.id && <Reprogramar turno={t} onCerrar={() => setReprogramando(null)} />}
              </FilaTurno>
            ))
          }
        </ContenidoDeLista>
      </Lista>
    </Pantalla>
  );
}

function PublicarHorario() {
  const toast = useToast();
  const [fecha, setFecha] = useState(fechaPropuesta);
  const [modalidad, setModalidad] = useState<Modalidad>("PRESENCIAL");
  const [publicar] = usePublicarHorarioMutation();
  const [ejecutar, ocupado] = useAccion();

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (!fecha) return void toast("Elegí fecha y hora", "err");
    if (!esFutura(fecha)) return void toast("Elegí una fecha y hora futuras", "err");
    void ejecutar(async () => {
      await publicar({ fechaHora: conSegundos(fecha), modalidad }).unwrap();
      toast("Horario publicado", "ok");
    });
  }

  return (
    <form className="card" onSubmit={enviar} noValidate>
      <h2 className="card-titulo">Publicar un horario disponible</h2>
      <div className="row-inline">
        <div className="form-group">
          <label htmlFor="agnFecha">Fecha y hora</label>
          {/* min: el selector no deja elegir fechas pasadas */}
          <input type="datetime-local" id="agnFecha" min={fechaParaInput(new Date())} value={fecha} onChange={(e) => setFecha(e.target.value)} />
        </div>
        <div className="form-group">
          <label htmlFor="agnModalidad">Modalidad</label>
          <select id="agnModalidad" value={modalidad} onChange={(e) => setModalidad(e.target.value as Modalidad)}>
            <option value="PRESENCIAL">Presencial</option>
            <option value="VIRTUAL">Virtual (teleconsulta)</option>
          </select>
        </div>
        <BotonAccion ocupado={ocupado} type="submit" className="btn-primary" id="btnPublicar">
          Publicar
        </BotonAccion>
      </div>
    </form>
  );
}

function Reprogramar({ turno, onCerrar }: { turno: Turno; onCerrar: () => void }) {
  const toast = useToast();
  const revisarPronto = useRevisarNotificacionesPronto();
  const [fecha, setFecha] = useState(String(turno.fechaHora).slice(0, 16));
  const [reprogramar] = useReprogramarTurnoMutation();
  const [ejecutar, ocupado] = useAccion();
  const idInput = "reproFecha-" + turno.id;

  function guardar(e: FormEvent) {
    e.preventDefault();
    if (!fecha) return void toast("Elegí la nueva fecha y hora", "err");
    if (!esFutura(fecha)) return void toast("Elegí una fecha y hora futuras", "err");
    void ejecutar(async () => {
      await reprogramar({ id: turno.id, fechaHora: conSegundos(fecha) }).unwrap();
      toast("Turno reprogramado", "ok");
      onCerrar();
      revisarPronto();
    });
  }

  return (
    <form onSubmit={guardar} noValidate id={"repro-" + turno.id}>
      <div className="row-inline" style={{ marginTop: 12 }}>
        <div className="form-group">
          <label htmlFor={idInput}>Nueva fecha y hora</label>
          <input type="datetime-local" id={idInput} min={fechaParaInput(new Date())} value={fecha} onChange={(e) => setFecha(e.target.value)} />
        </div>
        <BotonAccion ocupado={ocupado} type="submit" className="btn-primary btn-sm" id={"btnRepro-" + turno.id}>
          Guardar
        </BotonAccion>
        <button type="button" className="btn-secondary btn-sm" onClick={onCerrar}>
          Cancelar
        </button>
      </div>
      <p className="inline-hint">Si el turno tiene paciente, le llega una notificación con la fecha nueva.</p>
    </form>
  );
}
