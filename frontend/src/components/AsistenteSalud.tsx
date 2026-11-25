import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { useNavigate } from "react-router-dom";
import { mensajeDeError } from "../api/baseQuery";
import { useOrientarMutation } from "../api/mediconectaApi";
import { agregar, type Mensaje } from "../lib/asistente";

const MAX_CARACTERES = 500;

const SALUDO =
  "¡Hola! Contame qué te pasa y te digo con qué especialidad conviene sacar turno. " +
  "Por ejemplo: «me pica la piel y tengo ronchas desde hace una semana».";

/**
 * Asistente de salud: un botón flotante que abre una charla con el orientador de especialidad.
 * Cada mensaje se evalúa solo (no se manda el historial al modelo) y pasa por el mismo gateway
 * del servidor que valida, limita y tiene respaldo. La charla vive mientras dure la sesión del
 * paciente: el Layout lo monta con key = id del usuario, así otra cuenta empieza de cero.
 */
export function AsistenteSalud() {
  const [abierto, setAbierto] = useState(false);
  const [texto, setTexto] = useState("");
  const [mensajes, setMensajes] = useState<Mensaje[]>([]);
  const [orientar, { isLoading }] = useOrientarMutation();
  const navigate = useNavigate();
  const proximoId = useRef(1);
  const boton = useRef<HTMLButtonElement>(null);
  const entrada = useRef<HTMLTextAreaElement>(null);
  const fin = useRef<HTMLDivElement>(null);
  const idPanel = useId();
  const idTitulo = useId();

  useEffect(() => {
    if (abierto) entrada.current?.focus();
  }, [abierto]);

  useEffect(() => {
    fin.current?.scrollIntoView?.({ block: "end" });
  }, [mensajes, isLoading]);

  function cerrar() {
    setAbierto(false);
    boton.current?.focus();
  }

  async function enviar(e?: FormEvent) {
    e?.preventDefault();
    const sintomas = texto.trim();
    if (!sintomas || isLoading) return;
    setTexto("");
    setMensajes((l) => agregar(l, { id: proximoId.current++, de: "paciente", texto: sintomas }));
    let respuesta: Mensaje;
    try {
      const o = await orientar({ sintomas }).unwrap();
      respuesta = { id: proximoId.current++, de: "asistente", texto: o.motivo, orientacion: o };
    } catch (err) {
      respuesta = { id: proximoId.current++, de: "asistente", texto: mensajeDeError(err), error: true };
    }
    setMensajes((l) => agregar(l, respuesta));
  }

  function teclas(e: KeyboardEvent<HTMLTextAreaElement>) {
    // Enter envía; Shift+Enter hace un salto de línea.
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      void enviar();
    }
  }

  function sacarTurno(especialidad: string) {
    setAbierto(false);
    navigate("/agendar", { state: { especialidad } });
  }

  return (
    <div className="asistente" onKeyDown={(e) => e.key === "Escape" && abierto && cerrar()}>
      {abierto && (
        <section className="asistente-panel" id={idPanel} role="dialog" aria-modal="false" aria-labelledby={idTitulo}>
          <header className="asistente-cabecera">
            <div>
              <h2 className="asistente-titulo" id={idTitulo}>
                Asistente de salud
              </h2>
              <p className="asistente-sub">Te ayudo a elegir especialidad. No es un diagnóstico médico.</p>
            </div>
            <button type="button" className="asistente-cerrar" onClick={cerrar} aria-label="Cerrar el asistente">
              ✕
            </button>
          </header>
          <div className="asistente-mensajes" role="log" aria-live="polite" aria-label="Conversación con el asistente" id="asistMensajes">
            <Burbuja de="asistente">{SALUDO}</Burbuja>
            {mensajes.map((m) =>
              m.de === "paciente" ? (
                <Burbuja key={m.id} de="paciente">
                  {m.texto}
                </Burbuja>
              ) : (
                <RespuestaDelAsistente key={m.id} mensaje={m} onSacarTurno={sacarTurno} />
              ),
            )}
            {isLoading && (
              <Burbuja de="asistente">
                <span className="asistente-escribiendo">Pensando…</span>
              </Burbuja>
            )}
            <div ref={fin} />
          </div>
          <form className="asistente-entrada" onSubmit={enviar}>
            <label htmlFor="asistTexto" className="sr-only">
              Contanos qué te pasa
            </label>
            <textarea
              id="asistTexto"
              ref={entrada}
              rows={2}
              maxLength={MAX_CARACTERES}
              placeholder="¿Qué te pasa?"
              value={texto}
              onChange={(e) => setTexto(e.target.value)}
              onKeyDown={teclas}
            />
            <button type="submit" className="btn-primary btn-sm" id="asistEnviar" disabled={!texto.trim() || isLoading}>
              Enviar
            </button>
          </form>
          <p className="asistente-pie">No pongas tu nombre, DNI ni teléfono. {texto.length}/{MAX_CARACTERES}</p>
        </section>
      )}
      <button
        type="button"
        ref={boton}
        className={"asistente-boton" + (abierto ? " abierto" : "")}
        id="asistAbrir"
        aria-expanded={abierto}
        aria-controls={abierto ? idPanel : undefined}
        onClick={() => (abierto ? cerrar() : setAbierto(true))}
      >
        <span aria-hidden="true">{abierto ? "✕" : "💬"}</span>
        <span className="asistente-boton-texto">{abierto ? "Cerrar" : "¿Qué te pasa?"}</span>
      </button>
    </div>
  );
}

function Burbuja({ de, children, clase = "" }: { de: "paciente" | "asistente"; children: React.ReactNode; clase?: string }) {
  return (
    <div className={`burbuja burbuja-${de} ${clase}`.trim()}>
      <span className="sr-only">{de === "paciente" ? "Vos: " : "Asistente: "}</span>
      {children}
    </div>
  );
}

function RespuestaDelAsistente({ mensaje, onSacarTurno }: { mensaje: Extract<Mensaje, { de: "asistente" }>; onSacarTurno: (e: string) => void }) {
  const o = mensaje.orientacion;
  if (mensaje.error || !o) {
    return (
      <Burbuja de="asistente" clase="burbuja-error">
        {mensaje.texto}
      </Burbuja>
    );
  }
  if (o.tipo === "URGENCIA") {
    return (
      <Burbuja de="asistente" clase="burbuja-urgencia">
        <strong>Atención</strong>
        <p>{o.motivo}</p>
      </Burbuja>
    );
  }
  return (
    <Burbuja de="asistente">
      {o.especialidad ? (
        <>
          <p>
            Te sugerimos <strong className="asist-especialidad">{o.especialidad}</strong>
            {o.fuente === "RESPALDO" && <span className="pill pill-pendiente asist-respaldo">Sugerencia general</span>}
          </p>
          <p>{o.motivo}</p>
          <button type="button" className="btn-primary btn-sm asist-sacar-turno" onClick={() => onSacarTurno(o.especialidad!)}>
            Sacar turno de {o.especialidad}
          </button>
        </>
      ) : (
        <p>{o.motivo}</p>
      )}
      <p className="asistente-aviso">{o.aviso}</p>
    </Burbuja>
  );
}
