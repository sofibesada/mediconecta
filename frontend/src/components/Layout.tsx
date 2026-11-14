import { useEffect } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../app/hooks";
import { menuDe, tituloDe } from "../app/rutas";
import { salir } from "../features/sesion/salir";
import { temaElegido, type Tema } from "../features/ui/uiSlice";
import { iniciales, rolLindo } from "../lib/formato";
import { Toasts } from "./Toasts";
import { useAvisosDeNotificaciones } from "./useAvisosDeNotificaciones";

function Contador({ cantidad, clase, id }: { cantidad: number; clase: string; id: string }) {
  if (cantidad === 0) return null;
  return (
    <span className={clase} id={id}>
      {cantidad > 99 ? "99+" : cantidad}
      <span className="sr-only"> sin leer</span>
    </span>
  );
}

export function Layout() {
  const dispatch = useAppDispatch();
  const usuario = useAppSelector((s) => s.sesion.usuario);
  const tema = useAppSelector((s) => s.ui.tema);
  const { pathname } = useLocation();

  const logueado = !!usuario;
  const bloqueado = logueado && !!usuario.debeCambiarPassword;
  // Paciente y profesional reciben avisos de sus turnos; el administrador, los pedidos de
  // recuperación de contraseña.
  const conNotificaciones = logueado && !bloqueado;
  const sinLeer = useAvisosDeNotificaciones(conNotificaciones);

  useEffect(() => {
    if (tema === "estandar") delete document.documentElement.dataset.tema;
    else document.documentElement.dataset.tema = tema;
  }, [tema]);

  const titulo = tituloDe(pathname);
  useEffect(() => {
    document.title = titulo === "MediConecta" ? "MediConecta" : `${titulo} · MediConecta`;
  }, [titulo]);

  return (
    <>
      <Toasts />
      <div className="app-container">
        <aside className="sidebar">
          <div className="logo-section">
            <div className="user-badge" aria-hidden="true">
              {logueado ? iniciales(usuario.nombre) : "MC"}
            </div>
            <div>
              <div className="logo-text">MEDICONECTA</div>
              <div className="rol-tag">{logueado ? rolLindo(usuario.rol) : ""}</div>
            </div>
          </div>

          {logueado && !bloqueado && (
            <nav className="nav-section" id="navPrincipal" aria-label="Menú principal">
              <div className="nav-section-title">Menú</div>
              {menuDe(usuario.rol).map((m) => (
                <NavLink key={m.ruta} to={m.ruta} end className={({ isActive }) => "nav-item" + (isActive ? " activo" : "")}>
                  {m.texto}
                  {m.ruta === "/notificaciones" && <Contador cantidad={sinLeer} clase="nav-count" id="navNotifCount" />}
                </NavLink>
              ))}
            </nav>
          )}

          {logueado && (
            <div className="nav-section" id="navSalir" style={{ marginTop: "auto", borderTop: "1px solid var(--slate-200)", paddingTop: 16 }}>
              <button type="button" className="nav-item boton-salir" onClick={() => dispatch(salir())}>
                Cerrar Sesión
              </button>
            </div>
          )}
        </aside>

        <div className="main-content">
          <header className="header">
            <div className="header-title" id="headerTitle">
              {titulo}
            </div>
            <div className="header-right">
              <select
                className="tema-select"
                id="selTema"
                aria-label="Tema de la aplicación"
                value={tema}
                onChange={(e) => dispatch(temaElegido(e.target.value as Tema))}
              >
                <option value="estandar">🎨 Estándar</option>
                <option value="claro">☀️ Claro</option>
                <option value="oscuro">🌙 Oscuro</option>
              </select>
              {conNotificaciones && (
                <Link to="/notificaciones" className="bell" id="btnCampana" aria-label="Notificaciones" title="Notificaciones">
                  <span aria-hidden="true">🔔</span>
                  <Contador cantidad={sinLeer} clase="bell-count" id="campanaCount" />
                </Link>
              )}
              <div className="header-user">{logueado ? usuario.nombre : ""}</div>
            </div>
          </header>

          <main className="content-area">
            <Outlet />
          </main>
        </div>
      </div>
    </>
  );
}
