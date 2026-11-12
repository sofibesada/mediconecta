import type { ReactNode } from "react";
import type { Turno } from "../api/tipos";
import { estadoLindo, fmtFecha, modalidadLinda } from "../lib/formato";
import { Badge } from "./comunes";

type Props = {
  turno: Turno;
  /** Desde el punto de vista del profesional se muestra el paciente; si no, el profesional. */
  vistaProfesional: boolean;
  onUnirse?: (t: Turno) => void;
  onCancelar?: (t: Turno) => void;
  onReprogramar?: (t: Turno) => void;
  children?: ReactNode;
};

export function FilaTurno({ turno: t, vistaProfesional, onUnirse, onCancelar, onReprogramar, children }: Props) {
  const otro = vistaProfesional
    ? t.pacienteNombre
      ? "Paciente: " + t.pacienteNombre
      : "Sin paciente aún"
    : "con " + (t.profesionalNombre || "Profesional #" + t.profesionalId);

  const puedeUnirse = onUnirse && t.modalidad === "VIRTUAL" && t.estado === "CONFIRMADO";
  const puedeReprogramar = onReprogramar && (t.estado === "CONFIRMADO" || t.estado === "DISPONIBLE");
  const puedeCancelar = onCancelar && t.estado !== "CANCELADO" && t.estado !== "FINALIZADO";

  return (
    <div className="fila">
      <div className="fila-info">
        <div className="fila-titulo">{fmtFecha(t.fechaHora)}</div>
        <div className="fila-detalle">
          {otro} · {modalidadLinda(t.modalidad)}
          {t.urgente && (
            <>
              {" · "}
              <strong>Urgente</strong>
            </>
          )}
          {t.obraSocial && ` · ${t.obraSocial} (${t.codigoAutorizacion ?? ""})`}
        </div>
        <Badge clase={t.estado}>{estadoLindo(t.estado)}</Badge>
        {children}
      </div>
      {(puedeUnirse || puedeReprogramar || puedeCancelar) && (
        <div className="fila-acciones">
          {puedeUnirse && (
            <button type="button" className="btn-primary btn-sm" onClick={() => onUnirse(t)}>
              Unirse a la videollamada
            </button>
          )}
          {puedeReprogramar && (
            <button type="button" className="btn-secondary btn-sm" onClick={() => onReprogramar(t)}>
              Reprogramar
            </button>
          )}
          {puedeCancelar && (
            <button type="button" className="btn-danger btn-sm" onClick={() => onCancelar(t)}>
              Cancelar
            </button>
          )}
        </div>
      )}
    </div>
  );
}
