import { useState } from "react";
import { mensajeDeError } from "../api/baseQuery";
import { useOrientarMutation } from "../api/mediconectaApi";
import type { Orientacion } from "../api/tipos";
import { BotonAccion } from "./comunes";

const MAX_CARACTERES = 500;

/**
 * Orientador de especialidad (IA). El paciente cuenta qué le pasa y la app le sugiere con
 * qué especialidad sacar turno. Todo pasa por el gateway del servidor: si la IA falla o
 * tarda, igual llega una sugerencia (Clínica médica) y se avisa que es de respaldo.
 */
export function OrientadorEspecialidad({ onUsar }: { onUsar: (especialidad: string) => void }) {
  const [sintomas, setSintomas] = useState("");
  const [resultado, setResultado] = useState<Orientacion | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [orientar, { isLoading }] = useOrientarMutation();

  async function consultar(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setResultado(null);
    try {
      setResultado(await orientar({ sintomas }).unwrap());
    } catch (err) {
      setError(mensajeDeError(err));
    }
  }

  return (
    <div className="card" id="iaOrientador">
      <h2 className="card-titulo">¿No sabés qué especialidad elegir?</h2>
      <form onSubmit={consultar}>
        <div className="form-group">
          <label htmlFor="iaSintomas">Contanos qué te pasa</label>
          <textarea
            id="iaSintomas"
            rows={2}
            maxLength={MAX_CARACTERES}
            placeholder="Por ejemplo: me pica la piel y tengo ronchas desde hace una semana"
            value={sintomas}
            onChange={(e) => setSintomas(e.target.value)}
          />
          <p className="inline-hint">
            No pongas tu nombre, DNI ni teléfono. {sintomas.length}/{MAX_CARACTERES}
          </p>
        </div>
        <BotonAccion ocupado={isLoading} id="iaOrientar" type="submit" className="btn-secondary" disabled={!sintomas.trim()}>
          Sugerirme una especialidad
        </BotonAccion>
      </form>
      {error && (
        <p className="ia-error" id="iaError" role="alert">
          {error}
        </p>
      )}
      {resultado && <Resultado orientacion={resultado} onUsar={onUsar} />}
    </div>
  );
}

function Resultado({ orientacion, onUsar }: { orientacion: Orientacion; onUsar: (especialidad: string) => void }) {
  if (orientacion.tipo === "URGENCIA") {
    return (
      <div className="ia-resultado ia-urgencia" id="iaResultado" role="alert">
        <strong>Atención</strong>
        <p>{orientacion.motivo}</p>
      </div>
    );
  }
  return (
    <div className="ia-resultado" id="iaResultado" aria-live="polite">
      {orientacion.especialidad ? (
        <>
          <p>
            Te sugerimos <strong id="iaEspecialidad">{orientacion.especialidad}</strong>
            {orientacion.fuente === "RESPALDO" && (
              <span className="pill pill-pendiente" id="iaRespaldo" style={{ marginLeft: 8 }}>
                Sugerencia general
              </span>
            )}
          </p>
          <p>{orientacion.motivo}</p>
          <button type="button" className="btn-primary btn-sm" id="iaUsarEspecialidad" onClick={() => onUsar(orientacion.especialidad!)}>
            Ver profesionales de {orientacion.especialidad}
          </button>
        </>
      ) : (
        <p>{orientacion.motivo}</p>
      )}
      <p className="inline-hint">{orientacion.aviso}</p>
    </div>
  );
}
