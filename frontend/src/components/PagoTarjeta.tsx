import { useState, type FormEvent } from "react";
import { useAccion, useRevisarNotificacionesPronto, useToast } from "../app/hooks";
import { usePagarMutation } from "../api/mediconectaApi";
import type { Turno } from "../api/tipos";
import { fmtPesos, nuevaClave } from "../lib/formato";
import { errorDeTarjeta, formatear, formatearVencimiento, largoCvv, marca, soloDigitos, TARJETAS_DE_PRUEBA } from "../lib/tarjetas";
import { BotonAccion } from "./comunes";

/**
 * Pago con tarjeta de crédito o débito. Detecta la marca mientras se escribe, valida el
 * número (dígito verificador), el vencimiento y el código de seguridad antes de enviar, y
 * manda una Idempotency-Key: si el pedido se repite, no se cobra dos veces.
 */
export function PagoTarjeta({ turno, importe, onPagado, onCancelar }: { turno: Turno; importe: number; onPagado: () => void; onCancelar: () => void }) {
  const toast = useToast();
  const revisarPronto = useRevisarNotificacionesPronto();
  const [numero, setNumero] = useState("");
  const [vencimiento, setVencimiento] = useState("");
  const [cvv, setCvv] = useState("");
  const [titular, setTitular] = useState("");
  const [error, setError] = useState<string | null>(null);
  // Una clave por intento: si se corta la conexión se reintenta con la misma (no cobra dos
  // veces); si el servidor ya contestó (rechazo, datos inválidos), el próximo intento es otro pago.
  const [clave, setClave] = useState(nuevaClave);
  const [pagar] = usePagarMutation();
  const [ejecutar, ocupado] = useAccion();
  const m = marca(numero);
  const id = (campo: string) => `pago-${campo}-${turno.id}`;

  function enviar(e: FormEvent) {
    e.preventDefault();
    const problema = errorDeTarjeta({ numero, vencimiento, cvv, titular });
    setError(problema);
    if (problema) return;
    void ejecutar(async () => {
      try {
        const p = await pagar({ turnoId: turno.id, numero: soloDigitos(numero), vencimiento, cvv, titular: titular.trim(), clave }).unwrap();
        toast(`Pago aprobado con ${p.marca} terminada en ${p.ultimos4}`, "ok");
        revisarPronto();
        onPagado();
      } catch (err) {
        // El rechazo del banco se muestra en el formulario (además del aviso).
        const { mensaje: msg, status } = err as { mensaje?: string; status?: unknown };
        if (msg) setError(msg);
        if (status !== "SIN_CONEXION") setClave(nuevaClave());
        throw err;
      }
    });
  }

  return (
    <form className="tarjeta-form" onSubmit={enviar} noValidate aria-label="Pago con tarjeta">
      <div className="card-titulo" style={{ marginBottom: 10 }}>
        Pagar {fmtPesos(importe)} con tarjeta
      </div>
      <div className="form-group">
        <label htmlFor={id("numero")}>Número de tarjeta</label>
        <div className="tarjeta-numero">
          <input
            id={id("numero")}
            inputMode="numeric"
            autoComplete="cc-number"
            placeholder="0000 0000 0000 0000"
            value={numero}
            onChange={(e) => setNumero(formatear(e.target.value))}
          />
          <span className={"tarjeta-marca" + (m ? " reconocida" : "")} aria-live="polite">
            {m ?? "Marca"}
          </span>
        </div>
      </div>
      <div className="row-inline">
        <div className="form-group">
          <label htmlFor={id("venc")}>Vencimiento</label>
          <input id={id("venc")} inputMode="numeric" autoComplete="cc-exp" placeholder="MM/AA" value={vencimiento} onChange={(e) => setVencimiento(formatearVencimiento(e.target.value))} />
        </div>
        <div className="form-group">
          <label htmlFor={id("cvv")}>Código de seguridad</label>
          <input
            id={id("cvv")}
            inputMode="numeric"
            autoComplete="cc-csc"
            placeholder={m === "American Express" ? "4 dígitos" : "3 dígitos"}
            maxLength={largoCvv(m)}
            value={cvv}
            onChange={(e) => setCvv(soloDigitos(e.target.value).slice(0, largoCvv(m)))}
          />
        </div>
      </div>
      <div className="form-group">
        <label htmlFor={id("titular")}>Titular (como figura en la tarjeta)</label>
        <input id={id("titular")} autoComplete="cc-name" value={titular} onChange={(e) => setTitular(e.target.value.toUpperCase())} />
      </div>
      {error && (
        <div className="hold-box" role="alert" style={{ background: "var(--red-50)", borderColor: "var(--red-200)", marginTop: 0, marginBottom: 12 }}>
          {error}
        </div>
      )}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <BotonAccion ocupado={ocupado} type="submit" className="btn-primary" id={id("pagar")}>
          Pagar {fmtPesos(importe)}
        </BotonAccion>
        <button type="button" className="btn-secondary" onClick={onCancelar}>
          Volver
        </button>
      </div>
      <details className="inline-hint" style={{ marginTop: 10 }}>
        <summary>Tarjetas de prueba (pasarela simulada)</summary>
        <ul style={{ margin: "6px 0 0 18px" }}>
          {TARJETAS_DE_PRUEBA.map((t) => (
            <li key={t.numero}>
              <code>{t.numero}</code> · {t.resultado}
            </li>
          ))}
        </ul>
        Vencimiento: cualquier fecha futura. Código: 3 dígitos (American Express, 4).
      </details>
    </form>
  );
}
