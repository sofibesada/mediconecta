import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAccion, useAppDispatch, useAppSelector, useNombreObraSocial, useRevisarNotificacionesPronto, useToast, useUsuario } from "../app/hooks";
import { mensajeDeError } from "../api/baseQuery";
import {
  useAbandonarReservaMutation,
  useConfirmarMutation,
  useElegirProfesionalMutation,
  useEspecialidadesQuery,
  useLazyEstadoAsistenteQuery,
  useProfesionalesQuery,
  useReservarMutation,
} from "../api/mediconectaApi";
import type { EstadoAsistente, Modalidad, Turno } from "../api/tipos";
import { BotonAccion, Encabezado, Kv, Lista, Muted, Pantalla } from "../components/comunes";
import { coberturaRechazada, reservaTerminada, turnoRetenido } from "../features/agenda/agendaSlice";
import { cuentaRegresiva, fmtFecha, modalidadLinda, nuevaClave } from "../lib/formato";
import { filtrarHorarios } from "../lib/turnos";
import { atiende, resumenCartilla } from "../lib/cartilla";
import { OrientadorEspecialidad } from "../components/OrientadorEspecialidad";

/**
 * Agendar un turno. La reserva en curso la recuerda el SERVIDOR: un AsistenteAgendamiento
 * (@Stateful) por navegador, atado a la sesión HTTP. Si el paciente se va de la pantalla y
 * vuelve, el asistente le devuelve el profesional elegido y el turno que tenía retenido.
 */
export function Agendar() {
  const dispatch = useAppDispatch();
  const toast = useToast();
  const retenido = useAppSelector((s) => s.agenda.turnoRetenido);

  const profesionales = useProfesionalesQuery();
  const usuario = useUsuario();
  const nombreObraSocial = useNombreObraSocial();
  const [pedirEstado] = useLazyEstadoAsistenteQuery();
  const [elegir] = useElegirProfesionalMutation();
  const [reservarTurno] = useReservarMutation();

  const [asistente, setAsistente] = useState<EstadoAsistente | null>(null);
  const [profesionalId, setProfesionalId] = useState<number | "">("");
  const [horarios, setHorarios] = useState<{ cargando: boolean; error?: string; lista: Turno[] } | null>(null);
  // Filtros de búsqueda: especialidad (filtra profesionales), modalidad y fecha (filtran horarios).
  const [especialidad, setEspecialidad] = useState("");
  const [modalidad, setModalidad] = useState<"" | Modalidad>("");
  const [desde, setDesde] = useState("");
  // Solo los profesionales que atienden la obra social del paciente (cartilla).
  const [soloMiObraSocial, setSoloMiObraSocial] = useState(false);
  const especialidades = useEspecialidadesQuery();
  const profesionalesFiltrados = (profesionales.data ?? []).filter(
    (p) => (!especialidad || p.especialidad === especialidad) && (!soloMiObraSocial || atiende(p.obrasSociales, usuario.obraSocial)),
  );
  const elegido = profesionales.data?.find((p) => p.id === profesionalId);
  // Agrupados por especialidad (la opción muestra solo el nombre).
  const grupos = [...new Set(profesionalesFiltrados.map((p) => p.especialidad))].sort();
  // Si el paciente cambia de profesional antes de que llegue la respuesta, se ignora la vieja.
  const pedido = useRef(0);

  const mostrarHorarios = useCallback(
    async (id: number | "") => {
      if (!id) return setHorarios(null);
      const n = ++pedido.current;
      setHorarios({ cargando: true, lista: [] });
      try {
        const est = await elegir(id).unwrap();
        if (n !== pedido.current) return;
        setAsistente(est);
        setHorarios({ cargando: false, lista: est.horarios ?? [] });
      } catch (e) {
        if (n === pedido.current) setHorarios({ cargando: false, error: mensajeDeError(e), lista: [] });
      }
    },
    [elegir],
  );

  // Al entrar: se retoma la conversación del asistente, si había una.
  useEffect(() => {
    let vigente = true;
    pedirEstado()
      .unwrap()
      .then((est) => {
        if (!vigente) return;
        setAsistente(est);
        if (est.profesionalId) {
          setProfesionalId(est.profesionalId);
          void mostrarHorarios(est.profesionalId);
        }
        dispatch(turnoRetenido(est.turnoRetenido));
        if (est.turnoRetenido) toast("Retomamos tu reserva en curso", "info");
      })
      .catch((e) => vigente && toast(mensajeDeError(e), "err"));
    return () => {
      vigente = false;
    };
  }, [pedirEstado, mostrarHorarios, dispatch, toast]);

  async function reservar(turnoId: number) {
    try {
      const est = await reservarTurno(turnoId).unwrap();
      setAsistente(est);
      dispatch(turnoRetenido(est.turnoRetenido));
      toast("Turno reservado. Confirmalo antes de que venza la reserva.", "info");
      void mostrarHorarios(profesionalId);
    } catch (e) {
      toast(mensajeDeError(e), "err");
    }
  }

  /** Filtra por especialidad; si el profesional elegido es de otra, se deselecciona. */
  function elegirEspecialidad(esp: string) {
    setEspecialidad(esp);
    const actual = profesionales.data?.find((p) => p.id === profesionalId);
    if (esp && actual && actual.especialidad !== esp) {
      setProfesionalId("");
      void mostrarHorarios("");
    }
  }

  return (
    <Pantalla id="agendar">
      <Encabezado titulo="Agendar turno" subtitulo="Buscá por especialidad, fecha o modalidad y reservá un horario disponible" />
      <OrientadorEspecialidad onUsar={elegirEspecialidad} />
      <div className="card">
        <h2 className="card-titulo">1 · Buscá un turno</h2>
        <div className="row-inline" style={{ marginBottom: 16 }} id="agFiltros">
          <div className="form-group">
            <label htmlFor="agEspecialidad">Especialidad</label>
            <select
              id="agEspecialidad"
              value={especialidad}
              onChange={(e) => elegirEspecialidad(e.target.value)}
            >
              <option value="">Todas</option>
              {especialidades.data?.map((esp) => (
                <option key={esp} value={esp}>
                  {esp}
                </option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label htmlFor="agModalidad">Modalidad</label>
            <select id="agModalidad" value={modalidad} onChange={(e) => setModalidad(e.target.value as "" | Modalidad)}>
              <option value="">Presencial y virtual</option>
              <option value="PRESENCIAL">Solo presencial</option>
              <option value="VIRTUAL">Solo teleconsulta</option>
            </select>
          </div>
          <div className="form-group">
            <label htmlFor="agDesde">Desde el día</label>
            <input id="agDesde" type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
          </div>
        </div>
        {usuario.obraSocial && (
          <label className="check" style={{ marginBottom: 12 }}>
            <input
              type="checkbox"
              id="agSoloMiObraSocial"
              checked={soloMiObraSocial}
              onChange={(e) => {
                setSoloMiObraSocial(e.target.checked);
                if (e.target.checked && elegido && !atiende(elegido.obrasSociales, usuario.obraSocial)) {
                  setProfesionalId("");
                  void mostrarHorarios("");
                }
              }}
            />
            Mostrar solo quienes atienden {nombreObraSocial(usuario.obraSocial)}
          </label>
        )}
        <div className="form-group" style={{ marginBottom: 0 }}>
          <label htmlFor="agSelectProf">Profesional</label>
          <select
            id="agSelectProf"
            value={profesionalId}
            onChange={(e) => {
              const id = e.target.value ? Number(e.target.value) : "";
              setProfesionalId(id);
              void mostrarHorarios(id);
            }}
          >
            <option value="">{profesionales.isLoading ? "Cargando…" : "— Seleccioná —"}</option>
            {grupos.map((g) => (
              <optgroup key={g} label={g}>
                {profesionalesFiltrados
                  .filter((p) => p.especialidad === g)
                  .map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.nombre}
                    </option>
                  ))}
              </optgroup>
            ))}
          </select>
        </div>
        {elegido && (
          <p className="inline-hint" id="agCartilla">
            {!usuario.obraSocial ? (
              <>Atiende: {resumenCartilla(elegido.obrasSociales, nombreObraSocial)}.</>
            ) : atiende(elegido.obrasSociales, usuario.obraSocial) ? (
              <span className="pill pill-ok">Atiende {nombreObraSocial(usuario.obraSocial)}</span>
            ) : (
              <span className="pill pill-pendiente">No atiende {nombreObraSocial(usuario.obraSocial)}: el turno sería particular</span>
            )}
          </p>
        )}
        <div id="agDispLista" style={{ marginTop: 16 }}>
          {profesionales.error ? (
            <Muted>{mensajeDeError(profesionales.error)}</Muted>
          ) : profesionales.data?.length === 0 ? (
            <Muted>Todavía no hay profesionales cargados.</Muted>
          ) : profesionalesFiltrados.length === 0 ? (
            <Muted>No hay profesionales de {especialidad} por ahora.</Muted>
          ) : (
            horarios && <Horarios {...horarios} modalidad={modalidad} desde={desde} onReservar={reservar} />
          )}
        </div>
      </div>

      {retenido && (
        <div className="card" id="agCardHold">
          <h2 className="card-titulo">2 · Reserva temporal activa</h2>
          <Reserva
            key={retenido.id}
            turno={retenido}
            atiendeMiObraSocial={atiende(profesionales.data?.find((p) => p.id === retenido.profesionalId)?.obrasSociales, usuario.obraSocial)}
            onTerminada={() => {
              setAsistente(null);
              void mostrarHorarios(profesionalId);
            }}
          />
        </div>
      )}

      <p className="inline-hint" id="agAsistenteInfo">
        {asistente &&
          `Tu reserva en curso la guarda el servidor (asistente #${asistente.asistenteId} · ${asistente.pedidosAtendidos} ${
            asistente.pedidosAtendidos === 1 ? "paso" : "pasos"
          }).`}
      </p>
    </Pantalla>
  );
}

function Horarios({
  cargando,
  error,
  lista,
  modalidad,
  desde,
  onReservar,
}: {
  cargando: boolean;
  error?: string;
  lista: Turno[];
  modalidad: "" | Modalidad;
  desde: string;
  onReservar: (id: number) => void;
}) {
  if (cargando) return <Muted>Buscando horarios…</Muted>;
  if (error) return <Muted>{error}</Muted>;
  if (lista.length === 0) return <Muted>Este profesional no tiene horarios disponibles por ahora.</Muted>;
  const filtrados = filtrarHorarios(lista, modalidad, desde);
  if (filtrados.length === 0) return <Muted>Ningún horario de este profesional coincide con la modalidad o la fecha elegidas.</Muted>;
  const titulo = filtrados.length === lista.length ? "Horarios disponibles" : `Horarios disponibles (${filtrados.length} de ${lista.length})`;
  return (
    <Lista titulo={titulo}>
      {filtrados.map((t) => (
        <div className="fila" key={t.id}>
          <div className="fila-info">
            <div className="fila-titulo">{fmtFecha(t.fechaHora)}</div>
            <div className="fila-detalle">
              con {t.profesionalNombre || "Profesional #" + t.profesionalId} · {modalidadLinda(t.modalidad)}
            </div>
            <span className="badge badge-DISPONIBLE">Disponible</span>
          </div>
          <div className="fila-acciones">
            <button type="button" className="btn-primary btn-sm" onClick={() => onReservar(t.id)}>
              Reservar
            </button>
          </div>
        </div>
      ))}
    </Lista>
  );
}

/** El turno retenido, con la cuenta regresiva hasta que vence la reserva. */
function Reserva({ turno, atiendeMiObraSocial, onTerminada }: { turno: Turno; atiendeMiObraSocial: boolean; onTerminada: () => void }) {
  const usuario = useUsuario();
  const dispatch = useAppDispatch();
  const toast = useToast();
  const navigate = useNavigate();
  const revisarPronto = useRevisarNotificacionesPronto();
  const aviso = useAppSelector((s) => s.agenda.avisoCobertura);
  const nombreObraSocial = useNombreObraSocial(!!usuario.obraSocial);
  const [confirmarTurno] = useConfirmarMutation();
  const [abandonar] = useAbandonarReservaMutation();
  const [ejecutarNormal, ocupadoNormal] = useAccion();
  const [ejecutarParticular, ocupadoParticular] = useAccion();

  // Una clave por reserva y por modo de confirmacion: si se reintenta el mismo pedido, el
  // backend devuelve la confirmacion ya hecha en lugar de confirmar dos veces.
  const [claves] = useState(() => ({ normal: nuevaClave(), particular: nuevaClave() }));
  const [ahora, setAhora] = useState(Date.now());
  const { segundos, texto } = cuentaRegresiva(turno.holdExpiraEn, ahora);
  useEffect(() => {
    if (segundos === 0) return;
    const t = setInterval(() => setAhora(Date.now()), 1000);
    return () => clearInterval(t);
  }, [segundos]);
  const vencida = segundos === 0;

  // particular = true: confirma sin usar la obra social (por ejemplo, si la obra social rechazó).
  function confirmar(particular: boolean) {
    const ejecutar = particular ? ejecutarParticular : ejecutarNormal;
    return ejecutar(async () => {
      let t: Turno;
      try {
        t = await confirmarTurno({ particular, clave: particular ? claves.particular : claves.normal }).unwrap();
      } catch (e) {
        // Rechazo o falta de respuesta de la obra social: el backend revirtió todo y el turno
        // sigue reservado; se ofrece confirmarlo como particular.
        const msg = mensajeDeError(e);
        if (/obra social|no responde/i.test(msg)) dispatch(coberturaRechazada(msg));
        throw e;
      }
      dispatch(reservaTerminada()); // @Remove: la conversación terminó
      navigate("/confirmacion", { state: { turno: t } });
      toast("Turno confirmado", "ok");
      revisarPronto();
    });
  }

  async function soltar() {
    if (!window.confirm("¿Soltar este horario? Va a quedar disponible para otros pacientes.")) return;
    try {
      await abandonar().unwrap();
      dispatch(reservaTerminada());
      toast("Reserva cancelada", "ok");
      onTerminada();
    } catch (e) {
      toast(mensajeDeError(e), "err");
    }
  }

  return (
    <div id="agHoldContenido">
      <Kv label="Profesional">{turno.profesionalNombre || "—"}</Kv>
      <Kv label="Fecha">{fmtFecha(turno.fechaHora)}</Kv>
      <Kv label="Cobertura">{usuario.obraSocial ? `${nombreObraSocial(usuario.obraSocial)} · ${usuario.planObraSocial ?? ""}` : "Particular"}</Kv>
      {usuario.obraSocial && atiendeMiObraSocial && <p className="inline-hint">Al confirmar pedimos la autorización a tu obra social.</p>}
      {usuario.obraSocial && !atiendeMiObraSocial && (
        <p className="inline-hint" id="agFueraDeCartilla">
          Este profesional no atiende tu obra social: el turno se confirma como particular.
        </p>
      )}
      <div className="hold-box">
        La reserva vence en <strong>{texto}</strong>. Si expira, el turno vuelve a estar disponible.
      </div>
      {aviso && (
        <>
          <div className="hold-box" style={{ background: "var(--red-50)", borderColor: "var(--red-200)" }} role="alert">
            <strong style={{ color: "var(--red-600)" }}>No se pudo confirmar con tu obra social.</strong>
            <br />
            {aviso}
          </div>
          <BotonAccion ocupado={ocupadoParticular} id="btnParticular" style={{ marginTop: 16 }} disabled={vencida} onClick={() => confirmar(true)}>
            Confirmar como particular
          </BotonAccion>
        </>
      )}
      <BotonAccion
        ocupado={ocupadoNormal}
        id="btnConfirmar"
        className={(aviso ? "btn-secondary" : "btn-primary") + " btn-full"}
        style={{ marginTop: aviso ? 8 : 16 }}
        disabled={vencida}
        onClick={() => confirmar(!!usuario.obraSocial && !atiendeMiObraSocial)}
      >
        {aviso ? "Reintentar con la obra social" : usuario.obraSocial && !atiendeMiObraSocial ? "Confirmar como particular" : "Confirmar turno"}
      </BotonAccion>
      <button type="button" className="btn-secondary btn-full" style={{ marginTop: 8 }} onClick={soltar}>
        Soltar este horario
      </button>
    </div>
  );
}
