import type { ReactNode } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useAppSelector } from "./app/hooks";
import type { Rol } from "./api/tipos";
import { Layout } from "./components/Layout";
import { Administracion } from "./pages/Administracion";
import { Agendar } from "./pages/Agendar";
import { CambiarPassword } from "./pages/CambiarPassword";
import { Confirmacion } from "./pages/Confirmacion";
import { Facturacion } from "./pages/Facturacion";
import { HistoriaClinica } from "./pages/HistoriaClinica";
import { Ingreso } from "./pages/Ingreso";
import { Inicio } from "./pages/Inicio";
import { MiAgenda } from "./pages/MiAgenda";
import { MisCitas } from "./pages/MisCitas";
import { MisPacientes } from "./pages/MisPacientes";
import { Notificaciones } from "./pages/Notificaciones";
import { Perfil } from "./pages/Perfil";

/**
 * Guarda de las rutas privadas:
 * - sin sesión, al ingreso;
 * - con la contraseña temporal, solo se puede cambiarla (el backend tampoco deja hacer otra cosa);
 * - con un rol que no corresponde, al inicio.
 */
function Privada({ roles, children }: { roles?: Rol[]; children: ReactNode }) {
  const usuario = useAppSelector((s) => s.sesion.usuario);
  const { pathname } = useLocation();
  if (!usuario) return <Navigate to="/ingresar" replace />;
  if (usuario.debeCambiarPassword && pathname !== "/cambiar-password") return <Navigate to="/cambiar-password" replace />;
  if (!usuario.debeCambiarPassword && pathname === "/cambiar-password") return <Navigate to="/" replace />;
  if (roles && !roles.includes(usuario.rol)) return <Navigate to="/" replace />;
  return <>{children}</>;
}

function SoloSinSesion({ children }: { children: ReactNode }) {
  const usuario = useAppSelector((s) => s.sesion.usuario);
  if (usuario) return <Navigate to={usuario.debeCambiarPassword ? "/cambiar-password" : "/"} replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/ingresar" element={<SoloSinSesion><Ingreso /></SoloSinSesion>} />
        <Route path="/cambiar-password" element={<Privada><CambiarPassword /></Privada>} />
        <Route path="/" element={<Privada><Inicio /></Privada>} />

        <Route path="/agendar" element={<Privada roles={["PACIENTE"]}><Agendar /></Privada>} />
        <Route path="/confirmacion" element={<Privada roles={["PACIENTE"]}><Confirmacion /></Privada>} />
        <Route path="/mis-citas" element={<Privada roles={["PACIENTE"]}><MisCitas /></Privada>} />
        <Route path="/historia" element={<Privada roles={["PACIENTE"]}><HistoriaClinica /></Privada>} />

        <Route path="/mi-agenda" element={<Privada roles={["PROFESIONAL"]}><MiAgenda /></Privada>} />
        <Route path="/pacientes" element={<Privada roles={["PROFESIONAL"]}><MisPacientes /></Privada>} />
        <Route path="/pacientes/:pacienteId/historia" element={<Privada roles={["PROFESIONAL"]}><HistoriaClinica /></Privada>} />

        <Route path="/notificaciones" element={<Privada roles={["PACIENTE", "PROFESIONAL"]}><Notificaciones /></Privada>} />

        <Route path="/admin" element={<Privada roles={["ADMIN"]}><Administracion /></Privada>} />
        <Route path="/facturacion" element={<Privada roles={["ADMIN"]}><Facturacion /></Privada>} />

        <Route path="/perfil" element={<Privada><Perfil /></Privada>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
