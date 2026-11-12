import { useAccion, useToast } from "../app/hooks";
import { mensajeDeError } from "../api/baseQuery";
import { useMarcarLeidaMutation, useMarcarTodasLeidasMutation, useNotificacionesQuery } from "../api/mediconectaApi";
import { BotonAccion, ContenidoDeLista, Encabezado, Lista, Pantalla } from "../components/comunes";
import { fmtFecha } from "../lib/formato";

export function Notificaciones() {
  const toast = useToast();
  // Es la misma consulta que hace la campana: comparten la caché de RTK Query.
  const notificaciones = useNotificacionesQuery();
  const [marcarLeida] = useMarcarLeidaMutation();
  const [marcarTodas] = useMarcarTodasLeidasMutation();
  const [ejecutar, ocupado] = useAccion();
  const hayNoLeidas = notificaciones.data?.some((n) => !n.leida) ?? false;

  return (
    <Pantalla id="notificaciones">
      <Encabezado titulo="Notificaciones" subtitulo="Avisos sobre tus turnos" />
      <div style={{ marginBottom: 16, display: "flex", gap: 8, flexWrap: "wrap" }}>
        <button type="button" className="btn-secondary btn-sm" onClick={() => void notificaciones.refetch()}>
          Actualizar
        </button>
        <BotonAccion
          ocupado={ocupado}
          className="btn-secondary btn-sm"
          id="btnLeerTodas"
          disabled={!hayNoLeidas}
          onClick={() =>
            ejecutar(async () => {
              await marcarTodas().unwrap();
              toast("Notificaciones marcadas como leídas", "ok");
            })
          }
        >
          Marcar todas como leídas
        </BotonAccion>
      </div>
      <Lista titulo="Tus notificaciones" id="notifLista">
        <ContenidoDeLista cargando={notificaciones.isLoading} error={notificaciones.error} datos={notificaciones.data} vacio="No tenés notificaciones todavía.">
          {(lista) =>
            lista.map((n) => (
              <div className={"fila" + (n.leida ? "" : " no-leida")} key={n.id}>
                <div className="fila-info">
                  <div className="fila-titulo">
                    {!n.leida && <span className="sr-only">Sin leer: </span>}
                    {n.titulo}
                  </div>
                  <div className="fila-detalle">
                    {n.mensaje}
                    <br />
                    {fmtFecha(n.creadaEn)}
                  </div>
                </div>
                {!n.leida && (
                  <div className="fila-acciones">
                    <button
                      type="button"
                      className="btn-secondary btn-sm"
                      onClick={() => marcarLeida(n.id).unwrap().catch((e) => toast(mensajeDeError(e), "err"))}
                    >
                      Marcar como leída
                    </button>
                  </div>
                )}
              </div>
            ))
          }
        </ContenidoDeLista>
      </Lista>
    </Pantalla>
  );
}
