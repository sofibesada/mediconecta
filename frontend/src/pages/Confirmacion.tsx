import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useNombreObraSocial } from "../app/hooks";
import type { Turno } from "../api/tipos";
import { Kv, Pantalla } from "../components/comunes";
import { fmtFecha, fmtPesos, modalidadLinda } from "../lib/formato";

/** Resumen del turno recién confirmado (llega desde Agendar). */
export function Confirmacion() {
  const turno = (useLocation().state as { turno?: Turno } | null)?.turno;
  const navigate = useNavigate();
  const nombreObraSocial = useNombreObraSocial(!!turno?.obraSocial);
  if (!turno) return <Navigate to="/mis-citas" replace />;

  return (
    <Pantalla id="confirmacion">
      <div className="confirmation">
        <div className="confirmation-icon" aria-hidden="true">
          ✓
        </div>
        <h1 className="confirmation-title">Turno confirmado</h1>
        <p className="page-subtitle">Tu consulta quedó agendada</p>
        <div className="confirmation-card" id="confirmCard">
          <Kv label="Profesional">{turno.profesionalNombre || "—"}</Kv>
          <Kv label="Paciente">{turno.pacienteNombre || "—"}</Kv>
          <Kv label="Fecha">{fmtFecha(turno.fechaHora)}</Kv>
          <Kv label="Modalidad">{modalidadLinda(turno.modalidad)}</Kv>
          <Kv label="Cobertura">{turno.obraSocial ? nombreObraSocial(turno.obraSocial) : "Particular"}</Kv>
          {turno.codigoAutorizacion && <Kv label="Autorización">{turno.codigoAutorizacion}</Kv>}
          <Kv label={turno.obraSocial ? "Copago a tu cargo" : "A pagar"}>{fmtPesos(turno.importeCopago)}</Kv>
          <Kv label="Estado">{turno.estado}</Kv>
          {(turno.importeCopago ?? 0) > 0 && (
            <p className="inline-hint" style={{ marginTop: 10 }}>
              Podés pagarlo online con tarjeta desde Mis Citas (botón «Ver detalle») o abonarlo en el consultorio.
            </p>
          )}
          {turno.modalidad === "VIRTUAL" && (
            <p className="inline-hint" style={{ marginTop: 10 }}>
              Es una teleconsulta: en unos segundos vas a tener el botón &quot;Unirse a la videollamada&quot; en Mis Citas.
            </p>
          )}
        </div>
        <button type="button" className="btn-primary" style={{ minWidth: 260 }} onClick={() => navigate("/mis-citas")}>
          Ver mis citas
        </button>
      </div>
    </Pantalla>
  );
}
