import { useState, type FormEvent } from "react";
import { useAccion, useAppDispatch, useToast } from "../app/hooks";
import { useCambiarPasswordMutation } from "../api/mediconectaApi";
import { BotonAccion, Pantalla } from "../components/comunes";
import { sesionIniciada } from "../features/sesion/sesionSlice";

/** Cambio obligatorio de la contraseña temporal que dio el administrador. */
export function CambiarPassword() {
  const [actual, setActual] = useState("");
  const [nueva, setNueva] = useState("");
  const [nueva2, setNueva2] = useState("");
  const [cambiar] = useCambiarPasswordMutation();
  const [ejecutar, ocupado] = useAccion();
  const dispatch = useAppDispatch();
  const toast = useToast();

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (!actual || !nueva) return void toast("Completá los campos", "err");
    if (nueva !== nueva2) return void toast("Las contraseñas nuevas no coinciden", "err");
    void ejecutar(async () => {
      // El backend devuelve el usuario con un token nuevo, ya con su rol.
      dispatch(sesionIniciada(await cambiar({ passwordActual: actual, passwordNueva: nueva }).unwrap()));
      toast("Contraseña actualizada", "ok");
    });
  }

  return (
    <Pantalla id="cambiarPassword">
      <div className="login-container">
        <form className="login-card" onSubmit={enviar} noValidate>
          <div className="login-header">
            <div className="login-icon" aria-hidden="true">
              🔒
            </div>
            <h1 className="login-title" style={{ fontSize: 20 }}>
              Cambiá tu contraseña
            </h1>
            <p className="login-subtitle">Estás usando la contraseña temporal que te dio el administrador. Elegí una nueva para continuar.</p>
          </div>
          <div className="form-group">
            <label htmlFor="cpActual">Contraseña actual (temporal)</label>
            <input type="password" id="cpActual" placeholder="••••••••" autoComplete="current-password" value={actual} onChange={(e) => setActual(e.target.value)} />
          </div>
          <div className="form-group">
            <label htmlFor="cpNueva">Nueva contraseña</label>
            <input type="password" id="cpNueva" placeholder="••••••••" autoComplete="new-password" value={nueva} onChange={(e) => setNueva(e.target.value)} />
          </div>
          <div className="form-group">
            <label htmlFor="cpNueva2">Repetir nueva contraseña</label>
            <input type="password" id="cpNueva2" placeholder="••••••••" autoComplete="new-password" value={nueva2} onChange={(e) => setNueva2(e.target.value)} />
          </div>
          <BotonAccion ocupado={ocupado} type="submit" id="btnCambiarPass">
            Guardar y continuar
          </BotonAccion>
        </form>
      </div>
    </Pantalla>
  );
}
