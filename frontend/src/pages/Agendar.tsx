import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAccion, useAppDispatch, useAppSelector, useNombreObraSocial, useRevisarNotificacionesPronto, useToast, useUsuario } from "../app/hooks";
import { mensajeDeError } from "../api/baseQuery";
import {
  useAbandonarReservaMutation,
  useConfirmarMutation,
  useElegirProfesionalMutation,
  useLazyEstadoAsistenteQuery,
  useProfesionalesQuery,
  useReservarMutation,
} from "../api/mediconectaApi";
import type { EstadoAsistente, Turno } from "../api/tipos";
import { BotonAccion, Encabezado, Kv, Lista, Muted, Pantalla } from "../components/comunes";
import { coberturaRechazada, reservaTerminada, turnoRetenido } from "../features/agenda/agendaSlice";
import { cuentaRegresiva, fmtFecha, modalidadLinda } from "../lib/formato";

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
  const [pedirEstado] = useLazyEstadoAsistenteQuery();
  const [elegir] = useElegirProfesionalMutation();
  const [reservarTurno] = useReservarMutation();

  const [asistente, setAsistente] = useState<EstadoAsistente | null>(null);
  const [profesionalId, setProfesionalId] = useState<number | "">("");
  const [horarios, setHorarios] = useState<{ cargando: boolean; error?: string; lista: Turno[] } | null>(null);
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

  return (
    <Pantalla id="agendar">
      <Encabezado titulo="Agendar turno" subtitulo="Elegí un profesional y reservá un horario disponible" />
      <div className="card">
        <h2 className="card-titulo">1 · Elegí un profesional</h2>
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
            {profesionales.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.nombre}
              </option>
            ))}
          </select>
        </div>
        <div id="agDispLista" style={{ marginTop: 16 }}>
          {profesionales.error ? (
            <Muted>{mensajeDeError(profesionales.error)}</Muted>
          ) : profesionales.data?.length === 0 ? (
            <Muted>Todavía no hay profesionales cargados.</Muted>
          ) : (
            horarios && <Horarios {...horarios} onReservar={reservar} />
          )}
        </div>
      </div>

      {retenido && (
        <div className="card" id="agCardHold">
          <h2 className="card-titulo">2 · Reserva temporal activa</h2>
          <Reserva
            turno={retenido}
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

function Horarios({ cargando, error, lista, onReservar }: { cargando: boolean; error?: string; lista: Turno[]; onReservar: (id: number) => void }) {
  if (cargando) return <Muted>Buscando horarios…</Muted>;
  if (error) return <Muted>{error}</Muted>;
  if (lista.length === 0) return <Muted>Este profesional no tiene horarios disponibles por ahora.</Muted>;
  return (
    <Lista titulo="Horarios disponibles">
      {lista.map((t) => (
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
function Reserva({ turno, onTerminada }: { turno: Turno; onTerminada: () => void }) {
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
        t = await confirmarTurno({ particular }).unwrap();
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
      {usuario.obraSocial && <p className="inline-hint">Al confirmar pedimos la autorización a tu obra social.</p>}
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
        onClick={() => confirmar(false)}
      >
        {aviso ? "Reintentar con la obra social" : "Confirmar turno"}
      </BotonAccion>
      <button type="button" className="btn-secondary btn-full" style={{ marginTop: 8 }} onClick={soltar}>
        Soltar este horario
      </button>
    </div>
  );
}
