import { useState, type FormEvent } from "react";
import { useAccion, useAppDispatch, useToast, useUsuario } from "../app/hooks";
import { mensajeDeError } from "../api/baseQuery";
import { useGuardarCoberturaMutation, useObrasSocialesQuery, useQuitarCoberturaMutation } from "../api/mediconectaApi";
import type { ObraSocial, Usuario } from "../api/tipos";
import { BotonAccion, Encabezado, Kv, Muted, Pantalla } from "../components/comunes";
import { usuarioActualizado } from "../features/sesion/sesionSlice";
import { rolLindo } from "../lib/formato";

export function Perfil() {
  const usuario = useUsuario();
  return (
    <Pantalla id="perfil">
      <Encabezado titulo="Mi Perfil" subtitulo="Datos de tu cuenta" />
      <div className="card card-narrow">
        <h2 className="card-titulo">Cuenta</h2>
        <Kv label="ID de usuario">{usuario.id}</Kv>
        <Kv label="Nombre">{usuario.nombre}</Kv>
        <Kv label="Email">{usuario.email}</Kv>
        <Kv label="Rol">{rolLindo(usuario.rol)}</Kv>
      </div>
      {usuario.rol === "PACIENTE" && <Cobertura usuario={usuario} />}
    </Pantalla>
  );
}

/** Cobertura médica. La lista y la verificación vienen del sistema legado de obras sociales (SOAP). */
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

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (!obraSocial || !numeroAfiliado.trim()) return void toast("Elegí la obra social y completá el número de afiliado", "err");
    void ejecutar(async () => {
      const u = await guardar({ obraSocial, numeroAfiliado: numeroAfiliado.trim() }).unwrap();
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

  return (
    <form className="card card-narrow" id="cardCobertura" onSubmit={enviar} noValidate>
      <h2 className="card-titulo">Cobertura médica</h2>
      <div id="cobActual">
        {obras.error ? (
          <Muted sinPadding>{mensajeDeError(obras.error)}</Muted>
        ) : usuario.obraSocial ? (
          <>
            <Kv label="Obra social">{nombre(usuario.obraSocial)}</Kv>
            <Kv label="Plan">{usuario.planObraSocial || "—"}</Kv>
            <Kv label="Afiliado">{usuario.numeroAfiliado || "—"}</Kv>
            <Kv label="Copago">{usuario.copagoPorcentaje ?? 0}%</Kv>
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
          {obras.data?.map((o: ObraSocial) => (
            <option key={o.codigo} value={o.codigo}>
              {o.nombre}
              {o.tipo === "PREPAGA" ? " (prepaga)" : ""}
            </option>
          ))}
        </select>
      </div>
      <div className="form-group">
        <label htmlFor="cobAfiliado">Número de afiliado</label>
        <input type="text" id="cobAfiliado" placeholder="8 dígitos" inputMode="numeric" value={numeroAfiliado} onChange={(e) => setNumeroAfiliado(e.target.value)} />
      </div>
      <BotonAccion ocupado={ocupado} type="submit" id="btnCobertura">
        Verificar y guardar
      </BotonAccion>
      <p className="inline-hint">La verificamos en el momento con el sistema de tu obra social.</p>
    </form>
  );
}
