import { useState, type FormEvent } from "react";
import { useAccion, useAppDispatch, useToast } from "../app/hooks";
import { useLoginMutation, useRegistrarMutation } from "../api/mediconectaApi";
import type { Usuario } from "../api/tipos";
import { BotonAccion, Pantalla } from "../components/comunes";
import { sesionIniciada } from "../features/sesion/sesionSlice";
import { rolLindo } from "../lib/formato";

type Modo = "login" | "registro";

export function Ingreso() {
  const [modo, setModo] = useState<Modo>("login");
  const dispatch = useAppDispatch();
  const toast = useToast();

  // La navegación la resuelven las rutas: con la sesión iniciada, /ingresar lleva al inicio
  // (o a cambiar la contraseña temporal).
  function iniciar(u: Usuario) {
    dispatch(sesionIniciada(u));
    if (u.debeCambiarPassword) toast("Cambiá tu contraseña temporal para continuar", "info");
    else toast(`Sesión iniciada — ${u.nombre} (${rolLindo(u.rol)})`, "ok");
  }

  return (
    <Pantalla id="login">
      <div className="login-container">
        <div className="login-card">
          <div className="login-header">
            <div className="login-icon" aria-hidden="true">
              MC
            </div>
            <h1 className="login-title">MEDICONECTA</h1>
            <p className="login-subtitle">Plataforma de salud digital</p>
          </div>
          <div className="auth-toggle" role="tablist" aria-label="Ingresar o registrarse">
            <button
              type="button"
              role="tab"
              aria-selected={modo === "login"}
              className={"toggle-btn" + (modo === "login" ? " activo" : "")}
              id="tabLogin"
              onClick={() => setModo("login")}
            >
              Ingresar
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={modo === "registro"}
              className={"toggle-btn" + (modo === "registro" ? " activo" : "")}
              id="tabRegistro"
              onClick={() => setModo("registro")}
            >
              Registrarse
            </button>
          </div>
          {modo === "login" ? <FormLogin alIngresar={iniciar} /> : <FormRegistro alIngresar={iniciar} />}
        </div>
      </div>
    </Pantalla>
  );
}

function FormLogin({ alIngresar }: { alIngresar: (u: Usuario) => void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [login] = useLoginMutation();
  const [ejecutar, ocupado] = useAccion();
  const toast = useToast();

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (!email.trim() || !password) return void toast("Completá email y contraseña", "err");
    void ejecutar(async () => alIngresar(await login({ email: email.trim(), password }).unwrap()));
  }

  return (
    <form id="form-login" onSubmit={enviar} noValidate>
      <div className="form-group">
        <label htmlFor="loginEmail">Email</label>
        <input type="email" id="loginEmail" placeholder="tu@email.com" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} />
      </div>
      <div className="form-group">
        <label htmlFor="loginPass">Contraseña</label>
        <input type="password" id="loginPass" placeholder="••••••••" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
      </div>
      <BotonAccion ocupado={ocupado} type="submit" id="btnLogin">
        Ingresar
      </BotonAccion>
      <p className="inline-hint" style={{ textAlign: "center", marginTop: 12 }}>
        Los profesionales ingresan con el usuario que les dio el administrador.
      </p>
    </form>
  );
}

function FormRegistro({ alIngresar }: { alIngresar: (u: Usuario) => void }) {
  const [nombre, setNombre] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [registrar] = useRegistrarMutation();
  const [ejecutar, ocupado] = useAccion();
  const toast = useToast();

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (!nombre.trim() || !email.trim() || !password) return void toast("Completá nombre, email y contraseña", "err");
    void ejecutar(async () => alIngresar(await registrar({ nombre: nombre.trim(), email: email.trim(), password }).unwrap()));
  }

  return (
    <form id="form-registro" onSubmit={enviar} noValidate>
      <p className="inline-hint" style={{ marginBottom: 14 }}>
        El registro es solo para pacientes.
      </p>
      <div className="form-group">
        <label htmlFor="regNombre">Nombre</label>
        <input type="text" id="regNombre" placeholder="Tu nombre completo" autoComplete="name" value={nombre} onChange={(e) => setNombre(e.target.value)} />
      </div>
      <div className="form-group">
        <label htmlFor="regEmail">Email</label>
        <input type="email" id="regEmail" placeholder="tu@email.com" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
      </div>
      <div className="form-group">
        <label htmlFor="regPass">Contraseña</label>
        <input type="password" id="regPass" placeholder="••••••••" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
      </div>
      <BotonAccion ocupado={ocupado} type="submit" id="btnRegistro">
        Crear Cuenta
      </BotonAccion>
    </form>
  );
}
