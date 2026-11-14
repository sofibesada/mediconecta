import { useState, type FormEvent } from "react";
import { useAccion, useAppDispatch, useToast, useUsuario } from "../app/hooks";
import { mensajeDeError } from "../api/baseQuery";
import {
  useActualizarPerfilMutation,
  useCambiarPasswordMutation,
  useEspecialidadesQuery,
  useGuardarCoberturaMutation,
  useObrasSocialesQuery,
  useQuitarCoberturaMutation,
  useRecuperarDesdePerfilMutation,
} from "../api/mediconectaApi";
import type { ObraSocial, Usuario } from "../api/tipos";
import { BotonAccion, Encabezado, Kv, Muted, Pantalla } from "../components/comunes";
import { sesionIniciada, usuarioActualizado } from "../features/sesion/sesionSlice";
import { rolLindo } from "../lib/formato";

export function Perfil() {
  const usuario = useUsuario();
  return (
    <Pantalla id="perfil">
      <Encabezado titulo="Mi Perfil" subtitulo="Tus datos y la seguridad de tu cuenta" />
      <DatosCuenta usuario={usuario} />
      <CambioPassword usuario={usuario} />
      {usuario.rol === "PACIENTE" && <Cobertura usuario={usuario} />}
    </Pantalla>
  );
}

/** Nombre, email y (si es profesional) especialidad. Cambiar el email pide la contraseña. */
function DatosCuenta({ usuario }: { usuario: Usuario }) {
  const dispatch = useAppDispatch();
  const toast = useToast();
  const esProf = usuario.rol === "PROFESIONAL";
  const especialidades = useEspecialidadesQuery(undefined, { skip: !esProf });
  const [nombre, setNombre] = useState(usuario.nombre);
  const [email, setEmail] = useState(usuario.email);
  const [especialidad, setEspecialidad] = useState(usuario.especialidad ?? "");
  const [passwordActual, setPasswordActual] = useState("");
  const [actualizar] = useActualizarPerfilMutation();
  const [ejecutar, ocupado] = useAccion();
  const cambiaEmail = email.trim().toLowerCase() !== usuario.email.toLowerCase();

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (!nombre.trim() || !email.trim()) return void toast("Completá el nombre y el email", "err");
    if (cambiaEmail && !passwordActual) return void toast("Para cambiar el email ingresá tu contraseña actual", "err");
    void ejecutar(async () => {
      const u = await actualizar({ nombre: nombre.trim(), email: email.trim(), especialidad: esProf ? especialidad : undefined, passwordActual: passwordActual || undefined }).unwrap();
      dispatch(usuarioActualizado(u));
      setPasswordActual("");
      toast("Datos actualizados", "ok");
    });
  }

  return (
    <form className="card card-narrow" onSubmit={enviar} noValidate id="cardCuenta">
      <h2 className="card-titulo">Cuenta</h2>
      <Kv label="Rol">{rolLindo(usuario.rol)}</Kv>
      <div className="form-group" style={{ marginTop: 16 }}>
        <label htmlFor="perfNombre">Nombre</label>
        <input id="perfNombre" type="text" autoComplete="name" value={nombre} onChange={(e) => setNombre(e.target.value)} />
      </div>
      <div className="form-group">
        <label htmlFor="perfEmail">Email</label>
        <input id="perfEmail" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
      </div>
      {esProf && (
        <div className="form-group">
          <label htmlFor="perfEspecialidad">Especialidad</label>
          <select id="perfEspecialidad" value={especialidad} onChange={(e) => setEspecialidad(e.target.value)}>
            <option value="">— Seleccioná —</option>
            {especialidades.data?.map((esp) => (
              <option key={esp} value={esp}>
                {esp}
              </option>
            ))}
          </select>
        </div>
      )}
      {cambiaEmail && (
        <div className="form-group">
          <label htmlFor="perfPassActual">Contraseña actual (para cambiar el email)</label>
          <input id="perfPassActual" type="password" autoComplete="current-password" value={passwordActual} onChange={(e) => setPasswordActual(e.target.value)} />
        </div>
      )}
      <BotonAccion ocupado={ocupado} type="submit" id="btnGuardarPerfil">
        Guardar cambios
      </BotonAccion>
    </form>
  );
}

/** Cambio de contraseña, o pedido al administrador si no se recuerda la actual. */
function CambioPassword({ usuario }: { usuario: Usuario }) {
  const dispatch = useAppDispatch();
  const toast = useToast();
  const [actual, setActual] = useState("");
  const [nueva, setNueva] = useState("");
  const [nueva2, setNueva2] = useState("");
  const [cambiar] = useCambiarPasswordMutation();
  const [recuperar] = useRecuperarDesdePerfilMutation();
  const [ejecutar, ocupado] = useAccion();
  const [pedido, setPedido] = useState<string | null>(null);

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (!actual || !nueva) return void toast("Completá la contraseña actual y la nueva", "err");
    if (nueva !== nueva2) return void toast("Las contraseñas nuevas no coinciden", "err");
    void ejecutar(async () => {
      // El backend devuelve un token nuevo: se reemplaza la sesión.
      dispatch(sesionIniciada(await cambiar({ passwordActual: actual, passwordNueva: nueva }).unwrap()));
      setActual("");
      setNueva("");
      setNueva2("");
      toast("Contraseña actualizada", "ok");
    });
  }

  async function pedirAlAdministrador() {
    if (!window.confirm("¿Pedirle al administrador una contraseña temporal? Te la va a pasar para que ingreses y la cambies.")) return;
    try {
      setPedido((await recuperar().unwrap()).mensaje);
    } catch (e) {
      toast(mensajeDeError(e), "err");
    }
  }

  return (
    <form className="card card-narrow" onSubmit={enviar} noValidate id="cardPassword">
      <h2 className="card-titulo">Contraseña</h2>
      <div className="form-group">
        <label htmlFor="cpPerfActual">Contraseña actual</label>
        <input id="cpPerfActual" type="password" autoComplete="current-password" value={actual} onChange={(e) => setActual(e.target.value)} />
      </div>
      <div className="form-group">
        <label htmlFor="cpPerfNueva">Contraseña nueva</label>
        <input id="cpPerfNueva" type="password" autoComplete="new-password" value={nueva} onChange={(e) => setNueva(e.target.value)} />
      </div>
      <div className="form-group">
        <label htmlFor="cpPerfNueva2">Repetir contraseña nueva</label>
        <input id="cpPerfNueva2" type="password" autoComplete="new-password" value={nueva2} onChange={(e) => setNueva2(e.target.value)} />
      </div>
      <BotonAccion ocupado={ocupado} type="submit" id="btnCambiarPassPerfil">
        Cambiar contraseña
      </BotonAccion>
      {usuario.rol !== "ADMIN" && (
        <div style={{ marginTop: 14 }}>
          {pedido ? (
            <div className="cred-box" role="status">
              {pedido}
            </div>
          ) : (
            <button type="button" className="btn-secondary btn-sm" onClick={pedirAlAdministrador} id="btnRecuperarPerfil">
              No me acuerdo de mi contraseña: pedírsela al administrador
            </button>
          )}
        </div>
      )}
    </form>
  );
}

/** Cobertura médica. La lista y la verificación vienen del sistema de obras sociales (SOAP). */
function Cobertura({ usuario }: { usuario: Usuario }) {
  const dispatch = useAppDispatch();
  const toast = useToast();
  const obras = useObrasSocialesQuery();
  const [obraSocial, setObraSocial] = useState("");
  const [numeroAfiliado, setNumeroAfiliado] = useState("");
  const [guardar] = useGuardarCoberturaMutation();
  const [quitar] = useQuitarCoberturaMutation();
  const [ejecutar, ocupado] = useAccion();

  const nombre = (codigo?: string) => obras.data?.find((o) => o.codigo === codigo)?.nombre ?? codigo;
  const prepagas = obras.data?.filter((o) => o.tipo === "PREPAGA") ?? [];
  const obrasSociales = obras.data?.filter((o) => o.tipo !== "PREPAGA") ?? [];

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (!obraSocial || !numeroAfiliado.trim()) return void toast("Elegí la obra social y completá el número de afiliado", "err");
    void ejecutar(async () => {
      const u = await guardar({ obraSocial, numeroAfiliado: numeroAfiliado.replace(/\D/g, "") }).unwrap();
      dispatch(usuarioActualizado(u));
      setNumeroAfiliado("");
      toast("Cobertura verificada: " + u.planObraSocial, "ok");
    });
  }

  async function quitarCobertura() {
    if (!window.confirm("¿Quitar tu obra social? Vas a atenderte como particular.")) return;
    try {
      dispatch(usuarioActualizado(await quitar().unwrap()));
      toast("Obra social quitada", "ok");
    } catch (e) {
      toast(mensajeDeError(e), "err");
    }
  }

  const opcion = (o: ObraSocial) => (
    <option key={o.codigo} value={o.codigo}>
      {o.nombre}
    </option>
  );

  return (
    <form className="card card-narrow" id="cardCobertura" onSubmit={enviar} noValidate>
      <h2 className="card-titulo">Cobertura médica</h2>
      <div id="cobActual">
        {obras.error ? (
          <Muted sinPadding>{mensajeDeError(obras.error)}</Muted>
        ) : usuario.obraSocial ? (
          <>
            <Kv label="Obra social o prepaga">{nombre(usuario.obraSocial)}</Kv>
            <Kv label="Plan">{usuario.planObraSocial || "—"}</Kv>
            <Kv label="Afiliado">{usuario.numeroAfiliado || "—"}</Kv>
            <Kv label="Copago de cada consulta">{usuario.copagoPorcentaje ?? 0}%</Kv>
            <button type="button" className="btn-secondary btn-sm" style={{ marginTop: 12 }} onClick={quitarCobertura}>
              Quitar (atenderme como particular)
            </button>
          </>
        ) : (
          <p className="inline-hint" style={{ marginTop: 0 }}>
            No cargaste obra social: te atendés como particular.
          </p>
        )}
      </div>
      <div className="form-group" style={{ marginTop: 16 }}>
        <label htmlFor="cobObraSocial">Obra social o prepaga</label>
        <select id="cobObraSocial" value={obraSocial} onChange={(e) => setObraSocial(e.target.value)}>
          <option value="">{obras.isLoading ? "Cargando…" : "— Seleccioná —"}</option>
          {prepagas.length > 0 && <optgroup label="Prepagas">{prepagas.map(opcion)}</optgroup>}
          {obrasSociales.length > 0 && <optgroup label="Obras sociales">{obrasSociales.map(opcion)}</optgroup>}
        </select>
      </div>
      <div className="form-group">
        <label htmlFor="cobAfiliado">Número de afiliado</label>
        <input type="text" id="cobAfiliado" placeholder="Como figura en tu credencial, sin espacios" inputMode="numeric" maxLength={20} value={numeroAfiliado} onChange={(e) => setNumeroAfiliado(e.target.value)} />
      </div>
      <BotonAccion ocupado={ocupado} type="submit" id="btnCobertura">
        Verificar y guardar
      </BotonAccion>
      <p className="inline-hint">La verificamos en el momento con el sistema de tu obra social o prepaga.</p>
    </form>
  );
}
