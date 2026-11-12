import { Link } from "react-router-dom";
import { useUsuario } from "../app/hooks";
import { useTurnosDePacienteQuery, useTurnosDeProfesionalQuery } from "../api/mediconectaApi";
import { ContenidoDeLista, Encabezado, Lista, Pantalla } from "../components/comunes";
import { FilaTurno } from "../components/FilaTurno";

type Accion = { ruta: string; titulo: string; descripcion: string };

const ACCIONES: Record<string, Accion[]> = {
  ADMIN: [
    { ruta: "/admin", titulo: "Administración", descripcion: "Crear profesionales y ver usuarios" },
    { ruta: "/facturacion", titulo: "Facturación", descripcion: "Autorizaciones y reclamos a obras sociales" },
    { ruta: "/perfil", titulo: "Mi Perfil", descripcion: "Tus datos de cuenta" },
  ],
  PROFESIONAL: [
    { ruta: "/mi-agenda", titulo: "Mi Agenda", descripcion: "Publicá horarios y gestioná turnos" },
    { ruta: "/pacientes", titulo: "Mis Pacientes", descripcion: "Historia clínica de tus pacientes" },
    { ruta: "/perfil", titulo: "Mi Perfil", descripcion: "Tus datos" },
  ],
  PACIENTE: [
    { ruta: "/agendar", titulo: "Agendar un turno", descripcion: "Reservá una consulta" },
    { ruta: "/mis-citas", titulo: "Ver mis citas", descripcion: "Gestioná tus turnos" },
    { ruta: "/historia", titulo: "Historia clínica", descripcion: "Diagnósticos y recetas" },
    { ruta: "/perfil", titulo: "Mi Perfil", descripcion: "Tus datos" },
  ],
};

export function Inicio() {
  const usuario = useUsuario();
  const esAdmin = usuario.rol === "ADMIN";
  const esProf = usuario.rol === "PROFESIONAL";

  return (
    <Pantalla id="dashboard">
      <Encabezado
        id="dashSaludo"
        titulo={"Hola, " + (usuario.nombre || "")}
        subtitulo={esAdmin ? "Panel de administración" : esProf ? "Resumen de tu agenda" : "Tu actividad en MediConecta"}
      />
      {!esAdmin && <ProximosTurnos usuarioId={usuario.id} esProf={esProf} />}
      <h2 className="page-title section-gap">Acciones rápidas</h2>
      <div className="quick-actions" id="dashAcciones">
        {ACCIONES[usuario.rol].map((a) => (
          <Link key={a.ruta} to={a.ruta} className="action-box">
            <div className="action-title">{a.titulo}</div>
            <div className="action-description">{a.descripcion}</div>
          </Link>
        ))}
      </div>
    </Pantalla>
  );
}

function ProximosTurnos({ usuarioId, esProf }: { usuarioId: number; esProf: boolean }) {
  const deProf = useTurnosDeProfesionalQuery(usuarioId, { skip: !esProf });
  const dePac = useTurnosDePacienteQuery(usuarioId, { skip: esProf });
  const q = esProf ? deProf : dePac;
  const proximos = q.data?.filter((t) => t.estado !== "CANCELADO" && t.estado !== "FINALIZADO").slice(0, 5);

  return (
    <Lista titulo={esProf ? "Próximos turnos de tu agenda" : "Tus próximos turnos"} id="dashTurnos">
      <ContenidoDeLista cargando={q.isLoading} error={q.error} datos={proximos} vacio="No hay turnos por ahora.">
        {(turnos) => turnos.map((t) => <FilaTurno key={t.id} turno={t} vistaProfesional={esProf} />)}
      </ContenidoDeLista>
    </Lista>
  );
}
