import { useMemo } from "react";
import { Link } from "react-router-dom";
import { useUsuario } from "../app/hooks";
import { useTurnosDeProfesionalQuery } from "../api/mediconectaApi";
import { Actualizar, ContenidoDeLista, Encabezado, Lista, Pantalla } from "../components/comunes";
import { pacientesDe } from "../lib/pacientes";

export function MisPacientes() {
  const usuario = useUsuario();
  const turnos = useTurnosDeProfesionalQuery(usuario.id);
  const pacientes = useMemo(() => (turnos.data ? pacientesDe(turnos.data) : undefined), [turnos.data]);

  return (
    <Pantalla id="pacientes">
      <Encabezado titulo="Mis Pacientes" subtitulo="Pacientes con turnos en tu agenda" />
      <Actualizar onClick={() => void turnos.refetch()} />
      <Lista titulo="Pacientes" id="pacientesLista">
        <ContenidoDeLista cargando={turnos.isLoading} error={turnos.error} datos={pacientes} vacio="Todavía no tenés pacientes con turnos.">
          {(lista) =>
            lista.map((p) => (
              <div className="fila" key={p.id}>
                <div className="fila-info">
                  <div className="fila-titulo">{p.nombre}</div>
                  <div className="fila-detalle">Paciente #{p.id}</div>
                </div>
                <div className="fila-acciones">
                  <Link className="btn-secondary btn-sm boton-link" to={`/pacientes/${p.id}/historia`} state={{ nombre: p.nombre }}>
                    Ver historia
                  </Link>
                </div>
              </div>
            ))
          }
        </ContenidoDeLista>
      </Lista>
    </Pantalla>
  );
}
