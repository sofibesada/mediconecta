import { useFacturacionQuery } from "../api/mediconectaApi";
import { useNombreObraSocial } from "../app/hooks";
import { Actualizar, Badge, ContenidoDeLista, Encabezado, Lista, Pantalla } from "../components/comunes";
import { estadoLindo, fmtFecha, fmtPesos } from "../lib/formato";

export function Facturacion() {
  const f = useFacturacionQuery();
  const nombreObraSocial = useNombreObraSocial();

  return (
    <Pantalla id="facturacion">
      <Encabezado titulo="Facturación" subtitulo="Autorizaciones de las obras sociales, pagos con tarjeta y reclamos enviados por la cola de facturación" />
      <Actualizar onClick={() => void f.refetch()} />
      <div style={{ marginBottom: 24 }}>
        <Lista titulo="Autorizaciones (auditoría)" id="factAutorizaciones">
          <ContenidoDeLista cargando={f.isLoading} error={f.error} datos={f.data?.autorizaciones} vacio="Todavía no se pidieron autorizaciones.">
            {(lista) =>
              lista.map((a, i) => (
                <div className="fila" key={a.turnoId + "-" + i}>
                  <div className="fila-info">
                    <div className="fila-titulo">
                      Turno #{a.turnoId} · {nombreObraSocial(a.obraSocial)}
                    </div>
                    <div className="fila-detalle">
                      {a.codigoAutorizacion ? "Autorización " + a.codigoAutorizacion : a.motivo ?? ""}
                      <br />
                      {fmtFecha(a.fecha)}
                    </div>
                    <Badge clase={a.resultado}>{estadoLindo(a.resultado)}</Badge>
                  </div>
                </div>
              ))
            }
          </ContenidoDeLista>
        </Lista>
      </div>
      <div style={{ marginBottom: 24 }}>
        <Lista titulo="Pagos con tarjeta" id="factPagos">
          <ContenidoDeLista cargando={f.isLoading} error={f.error} datos={f.data?.pagos} vacio="Todavía no hay pagos con tarjeta.">
            {(lista) =>
              lista.map((p) => (
                <div className="fila" key={p.id}>
                  <div className="fila-info">
                    <div className="fila-titulo">
                      Turno #{p.turnoId} · {fmtPesos(p.importe)}
                    </div>
                    <div className="fila-detalle">
                      {p.marca ?? "Tarjeta"} terminada en {p.ultimos4 ?? "—"}
                      {p.codigoAutorizacion && " · Autorización " + p.codigoAutorizacion}
                      {p.motivo && " · " + p.motivo}
                      <br />
                      {fmtFecha(p.creadoEn)}
                    </div>
                    <Badge clase={p.estado === "APROBADO" ? "AUTORIZADA" : "RECHAZADA"}>{p.estado}</Badge>
                  </div>
                </div>
              ))
            }
          </ContenidoDeLista>
        </Lista>
      </div>
      <Lista titulo="Reclamos a obras sociales" id="factReclamos">
        <ContenidoDeLista cargando={f.isLoading} error={f.error} datos={f.data?.reclamos} vacio="Todavía no hay reclamos.">
          {(lista) =>
            lista.map((r, i) => (
              <div className="fila" key={r.turnoId + "-" + i}>
                <div className="fila-info">
                  <div className="fila-titulo">
                    Turno #{r.turnoId} · {nombreObraSocial(r.obraSocial)} · {fmtPesos(r.importe)}
                  </div>
                  <div className="fila-detalle">
                    Autorización {r.codigoAutorizacion}
                    {r.numeroReclamo && " · Reclamo " + r.numeroReclamo}
                    {r.motivo && " · " + r.motivo}
                    <br />
                    {fmtFecha(r.creadoEn)}
                  </div>
                  <Badge clase={r.estado}>{r.estado}</Badge>
                </div>
              </div>
            ))
          }
        </ContenidoDeLista>
      </Lista>
    </Pantalla>
  );
}
