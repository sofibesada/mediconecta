import { useState, type FormEvent } from "react";
import { useAccion, useToast } from "../app/hooks";
import { mensajeDeError } from "../api/baseQuery";
import { useCambiarEstadoUsuarioMutation, useCrearProfesionalMutation, useResetearPasswordMutation, useUsuariosQuery } from "../api/mediconectaApi";
import type { Usuario } from "../api/tipos";
import { Actualizar, BotonAccion, ContenidoDeLista, Encabezado, Lista, Pantalla } from "../components/comunes";
import { rolLindo } from "../lib/formato";

export function Administracion() {
  const usuarios = useUsuariosQuery();
  return (
    <Pantalla id="admin">
      <Encabezado titulo="Administración" subtitulo="Alta de profesionales y usuarios del sistema" />
      <CrearProfesional />
      <div style={{ margin: "24px 0 12px" }}>
        <Actualizar onClick={() => void usuarios.refetch()} texto="Actualizar listado" />
      </div>
      <Lista titulo="Usuarios" id="adminUsuarios">
        <ContenidoDeLista cargando={usuarios.isLoading} error={usuarios.error} datos={usuarios.data} vacio="No hay usuarios.">
          {(lista) => lista.map((u) => <FilaUsuario key={u.id} usuario={u} />)}
        </ContenidoDeLista>
      </Lista>
    </Pantalla>
  );
}

function CrearProfesional() {
  const toast = useToast();
  const [nombre, setNombre] = useState("");
  const [email, setEmail] = useState("");
  const [passwordTemporal, setPasswordTemporal] = useState("");
  const [creado, setCreado] = useState<{ email: string; password: string } | null>(null);
  const [crear] = useCrearProfesionalMutation();
  const [ejecutar, ocupado] = useAccion();

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (!nombre.trim() || !email.trim() || !passwordTemporal) return void toast("Completá nombre, email y contraseña temporal", "err");
    void ejecutar(async () => {
      const p = await crear({ nombre: nombre.trim(), email: email.trim(), passwordTemporal }).unwrap();
      setCreado({ email: p.email, password: passwordTemporal });
      setNombre("");
      setEmail("");
      setPasswordTemporal("");
      toast("Profesional creado", "ok");
    });
  }

  return (
    <form className="card card-narrow" onSubmit={enviar} noValidate>
      <h2 className="card-titulo">Crear un profesional</h2>
      <div className="form-group">
        <label htmlFor="npNombre">Nombre</label>
        <input type="text" id="npNombre" placeholder="ej. Dra. Carla Ruiz" value={nombre} onChange={(e) => setNombre(e.target.value)} />
      </div>
      <div className="form-group">
        <label htmlFor="npEmail">Email</label>
        <input type="email" id="npEmail" placeholder="profesional@email.com" value={email} onChange={(e) => setEmail(e.target.value)} />
      </div>
      <div className="form-group">
        <label htmlFor="npPass">Contraseña temporal</label>
        <input type="text" id="npPass" placeholder="ej. Temporal2026" autoComplete="off" value={passwordTemporal} onChange={(e) => setPasswordTemporal(e.target.value)} />
      </div>
      <BotonAccion ocupado={ocupado} type="submit" id="btnCrearProf">
        Crear profesional
      </BotonAccion>
      <div id="npResultado">
        {creado && (
          <div className="cred-box">
            Profesional creado. Entregale estos datos para su primer ingreso:
            <br />
            <strong>Email:</strong> {creado.email}
            <br />
            <strong>Contraseña temporal:</strong> {creado.password}
            <br />
            Al ingresar, el sistema le va a pedir que la cambie.
          </div>
        )}
      </div>
    </form>
  );
}

function FilaUsuario({ usuario: u }: { usuario: Usuario }) {
  const toast = useToast();
  const [cambiarEstado] = useCambiarEstadoUsuarioMutation();
  const [resetear] = useResetearPasswordMutation();
  const [temporal, setTemporal] = useState<string | null>(null);

  async function alternarActivo() {
    const activo = !u.activo;
    if (!activo && !window.confirm("¿Desactivar este usuario? No va a poder ingresar.")) return;
    try {
      await cambiarEstado({ id: u.id, activo }).unwrap();
      toast(activo ? "Usuario activado" : "Usuario desactivado", "ok");
    } catch (e) {
      toast(mensajeDeError(e), "err");
    }
  }

  async function resetearPassword() {
    if (!window.confirm(`¿Generar una contraseña temporal nueva para ${u.nombre}? La actual dejará de funcionar.`)) return;
    try {
      setTemporal((await resetear(u.id).unwrap()).passwordTemporal);
      toast("Contraseña temporal generada", "ok");
    } catch (e) {
      toast(mensajeDeError(e), "err");
    }
  }

  return (
    <div className="fila">
      <div className="fila-info">
        <div className="fila-titulo">{u.nombre}</div>
        <div className="fila-detalle">
          {u.email} · <span className="badge badge-rol">{rolLindo(u.rol)}</span>
          {!u.activo && (
            <>
              {" · "}
              <strong style={{ color: "var(--red-600)" }}>inactivo</strong>
            </>
          )}
          {u.debeCambiarPassword && (
            <>
              {" · "}
              <em>contraseña sin cambiar</em>
            </>
          )}
        </div>
        {temporal && (
          <div className="cred-box">
            Contraseña temporal para <strong>{u.nombre}</strong>: <strong>{temporal}</strong>
            <br />
            Al ingresar, el sistema le va a pedir que la cambie.
          </div>
        )}
      </div>
      {u.rol !== "ADMIN" && (
        <div className="fila-acciones">
          <button type="button" className="btn-secondary btn-sm" onClick={resetearPassword}>
            Resetear contraseña
          </button>
          <button type="button" className={(u.activo ? "btn-danger" : "btn-secondary") + " btn-sm"} onClick={alternarActivo}>
            {u.activo ? "Desactivar" : "Activar"}
          </button>
        </div>
      )}
    </div>
  );
}
