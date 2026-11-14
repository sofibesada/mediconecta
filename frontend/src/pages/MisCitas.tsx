import { useState } from "react";
import { useRevisarNotificacionesPronto, useToast, useUsuario } from "../app/hooks";
import { mensajeDeError } from "../api/baseQuery";
import { useCancelarTurnoMutation, useTurnosDePacienteQuery } from "../api/mediconectaApi";
import type { Turno } from "../api/tipos";
import { Actualizar, ContenidoDeLista, Encabezado, Lista, Pantalla } from "../components/comunes";
import { DetalleCita } from "../components/DetalleCita";
import { FilaTurno } from "../components/FilaTurno";
import { useVideollamada } from "../components/useVideollamada";

export function MisCitas() {
  const usuario = useUsuario();
  const toast = useToast();
  const revisarPronto = useRevisarNotificacionesPronto();
  const unirse = useVideollamada();
  const turnos = useTurnosDePacienteQuery(usuario.id);
  const [cancelar] = useCancelarTurnoMutation();
  const [abierto, setAbierto] = useState<number | null>(null);

  async function cancelarTurno(t: Turno) {
    if (!window.confirm("¿Cancelar este turno?")) return;
    try {
      await cancelar({ id: t.id, version: t.version }).unwrap();
      toast("Turno cancelado", "ok");
      revisarPronto();
    } catch (e) {
      toast(mensajeDeError(e), "err");
    }
  }

  return (
    <Pantalla id="misCitas">
      <Encabezado titulo="Mis Citas" subtitulo="Tus turnos: tocá «Ver detalle» para ver cuánto cuesta cada uno y pagarlo online" />
      <Actualizar onClick={() => void turnos.refetch()} />
      <Lista titulo="Todos tus turnos" id="misCitasLista">
        <ContenidoDeLista cargando={turnos.isLoading} error={turnos.error} datos={turnos.data} vacio="Todavía no agendaste ningún turno.">
          {(lista) =>
            lista.map((t) => (
              <FilaTurno
                key={t.id}
                turno={t}
                vistaProfesional={false}
                onUnirse={unirse}
                onCancelar={cancelarTurno}
                onDetalle={() => setAbierto(abierto === t.id ? null : t.id)}
                detalleAbierto={abierto === t.id}
              >
                {abierto === t.id && <DetalleCita turno={t} />}
              </FilaTurno>
            ))
          }
        </ContenidoDeLista>
      </Lista>
    </Pantalla>
  );
}
