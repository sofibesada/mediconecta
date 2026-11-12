import type { ReactNode } from "react";
import { mensajeDeError } from "../api/baseQuery";

/** Pantalla de la aplicación. El id y la clase "activa" son los que usan las pruebas E2E. */
export function Pantalla({ id, children }: { id: string; children: ReactNode }) {
  return (
    <section className="pantalla activa" id={id}>
      {children}
    </section>
  );
}

export function Encabezado({ titulo, subtitulo, id }: { titulo: ReactNode; subtitulo?: ReactNode; id?: string }) {
  return (
    <>
      <h1 className="page-title" id={id}>
        {titulo}
      </h1>
      {subtitulo && <p className="page-subtitle">{subtitulo}</p>}
    </>
  );
}

export function Lista({ titulo, id, children }: { titulo: ReactNode; id?: string; children: ReactNode }) {
  return (
    <div className="lista">
      <h2 className="lista-header">{titulo}</h2>
      <div id={id}>{children}</div>
    </div>
  );
}

export function Muted({ children, sinPadding = false }: { children: ReactNode; sinPadding?: boolean }) {
  return (
    <div className="muted" style={sinPadding ? { padding: 0 } : undefined}>
      {children}
    </div>
  );
}

/**
 * Contenido de una lista que viene de la API: "Cargando…", el error, el mensaje de vacío o
 * los elementos.
 */
export function ContenidoDeLista<T>({
  cargando,
  error,
  datos,
  vacio,
  children,
}: {
  cargando: boolean;
  error?: unknown;
  datos?: T[];
  vacio: string;
  children: (items: T[]) => ReactNode;
}) {
  if (cargando && !datos) return <Muted>Cargando…</Muted>;
  if (error) return <Muted>{mensajeDeError(error)}</Muted>;
  if (!datos || datos.length === 0) return <Muted>{vacio}</Muted>;
  return <>{children(datos)}</>;
}

export function Kv({ label, children }: { label: ReactNode; children: ReactNode }) {
  return (
    <div className="kv">
      <span className="kv-label">{label}</span>
      <span className="kv-value">{children}</span>
    </div>
  );
}

export function Badge({ clase, children }: { clase: string; children: ReactNode }) {
  return <span className={"badge badge-" + clase}>{children}</span>;
}

/** Botón que muestra "…" y queda deshabilitado mientras la acción está en curso. */
export function BotonAccion({
  ocupado,
  children,
  className = "btn-primary btn-full",
  ...props
}: { ocupado: boolean; children: ReactNode } & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button className={className} disabled={ocupado || props.disabled} aria-busy={ocupado} {...props}>
      {ocupado ? "…" : children}
    </button>
  );
}

export function Actualizar({ onClick, texto = "Actualizar" }: { onClick: () => void; texto?: string }) {
  return (
    <div style={{ marginBottom: 16 }}>
      <button type="button" className="btn-secondary btn-sm" onClick={onClick}>
        {texto}
      </button>
    </div>
  );
}
