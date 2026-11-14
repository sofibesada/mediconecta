import { useState } from "react";
import { useNombreObraSocial } from "../app/hooks";
import type { Turno } from "../api/tipos";
import { estadoLindo, fmtFecha, fmtPesos, modalidadLinda } from "../lib/formato";
import { resumenDePago } from "../lib/turnos";
import { Kv } from "./comunes";
import { PagoTarjeta } from "./PagoTarjeta";

/** Toda la información de una cita: profesional, cobertura, cuánto cuesta y cuánto hay que pagar. */
export function DetalleCita({ turno: t }: { turno: Turno }) {
  const nombreObraSocial = useNombreObraSocial(!!t.obraSocial);
  const [pagando, setPagando] = useState(false);
  const r = resumenDePago(t);
  const pago = t.pago;

  return (
    <div className="detalle-cita" id={"detalle-" + t.id}>
      <Kv label="Profesional">
        {t.profesionalNombre || "—"}
        {t.profesionalEspecialidad ? ` · ${t.profesionalEspecialidad}` : ""}
      </Kv>
      <Kv label="Fecha y hora">{fmtFecha(t.fechaHora)}</Kv>
      <Kv label="Modalidad">{modalidadLinda(t.modalidad)}</Kv>
      <Kv label="Estado">{estadoLindo(t.estado)}</Kv>
      <Kv label="Cobertura">{t.obraSocial ? nombreObraSocial(t.obraSocial) : "Particular"}</Kv>
      {t.codigoAutorizacion && <Kv label="Autorización de la obra social">{t.codigoAutorizacion}</Kv>}
      {r.valorConsulta != null && <Kv label="Valor de la consulta">{fmtPesos(r.valorConsulta)}</Kv>}
      {r.cubreObraSocial > 0 && <Kv label="Cubre tu obra social">{fmtPesos(r.cubreObraSocial)}</Kv>}
      {r.aPagar != null && <Kv label={t.obraSocial ? "Copago a tu cargo" : "A tu cargo"}>{fmtPesos(r.aPagar)}</Kv>}
      <Kv label="Pago">
        {pago
          ? `Pagado con ${pago.marca} terminada en ${pago.ultimos4} el ${fmtFecha(pago.pagadoEn)}`
          : t.estado === "CANCELADO"
            ? "Sin cargo (turno cancelado)"
            : r.aPagar && r.aPagar > 0
              ? "Pendiente"
              : "Sin cargo"}
      </Kv>

      {r.puedePagar && !pagando && (
        <div className="hold-box" style={{ marginTop: 12 }}>
          Tenés que pagar <strong>{fmtPesos(r.aPagar)}</strong>
          {t.modalidad === "VIRTUAL" ? ". Pagalo online antes de la teleconsulta." : ": pagalo online ahora o llevalo el día del turno al consultorio."}
          <div style={{ marginTop: 10 }}>
            <button type="button" className="btn-primary btn-sm" onClick={() => setPagando(true)} id={"btnPagar-" + t.id}>
              Pagar online
            </button>
          </div>
        </div>
      )}
      {pagando && r.aPagar != null && <PagoTarjeta turno={t} importe={r.aPagar} onPagado={() => setPagando(false)} onCancelar={() => setPagando(false)} />}
    </div>
  );
}
