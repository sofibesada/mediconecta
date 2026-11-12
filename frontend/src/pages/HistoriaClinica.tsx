import { useState, type FormEvent } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { useAccion, useNombreObraSocial, useToast, useUsuario } from "../app/hooks";
import { mensajeDeError } from "../api/baseQuery";
import { useFichaQuery, useHistoriaQuery, useRegistrarDiagnosticoMutation, useRegistrarRecetaMutation } from "../api/mediconectaApi";
import type { CoberturaReceta, FichaPaciente } from "../api/tipos";
import { Actualizar, BotonAccion, ContenidoDeLista, Encabezado, Kv, Lista, Muted, Pantalla } from "../components/comunes";
import { fmtFecha } from "../lib/formato";

/**
 * Historia clínica. El paciente ve la suya (/historia) y solo la puede consultar; el
 * profesional ve la de sus pacientes (/pacientes/:id/historia) y puede cargar diagnósticos y
 * recetas. El backend controla que ese paciente sea realmente suyo.
 */
export function HistoriaClinica() {
  const usuario = useUsuario();
  const params = useParams();
  const nombreDesdeLista = (useLocation().state as { nombre?: string } | null)?.nombre;
  const propia = !params.pacienteId;
  const pacienteId = propia ? usuario.id : Number(params.pacienteId);

  const historia = useHistoriaQuery(pacienteId);
  // La ficha trae los datos del paciente y el descuento de cada receta según su obra social
  // (lo consulta el backend al sistema legado por SOAP).
  const ficha = useFichaQuery(pacienteId);
  const nombreObraSocial = useNombreObraSocial();
  const nombre = nombreDesdeLista ?? ficha.data?.nombre ?? "Paciente #" + pacienteId;
  const coberturaDe = new Map((ficha.data?.recetas ?? []).map((c) => [c.recetaId, c]));

  return (
    <Pantalla id="historia">
      {!propia && (
        <Link className="link-back" id="histVolver" to="/pacientes">
          ← Volver a Mis Pacientes
        </Link>
      )}
      <Encabezado
        id="histTitulo"
        titulo={propia ? "Historia Clínica" : "Historia de " + nombre}
        subtitulo={propia ? "Tus diagnósticos y recetas" : "Diagnósticos y recetas del paciente"}
      />
      <Actualizar
        onClick={() => {
          void historia.refetch();
          void ficha.refetch();
        }}
      />
      <div className="card" id="histFicha">
        <h2 className="card-titulo">Datos del paciente</h2>
        {ficha.isLoading ? (
          <Muted sinPadding>Cargando…</Muted>
        ) : ficha.error ? (
          <Muted sinPadding>{mensajeDeError(ficha.error)}</Muted>
        ) : (
          ficha.data && <Ficha ficha={ficha.data} nombreObraSocial={nombreObraSocial} />
        )}
      </div>
      <div className="grid-2">
        <Lista titulo="Diagnósticos" id="histDiag">
          <ContenidoDeLista cargando={historia.isLoading} error={historia.error} datos={historia.data?.diagnosticos} vacio="Sin diagnósticos.">
            {(lista) =>
              lista.map((d) => (
                <div className="fila" key={d.id}>
                  <div className="fila-info">
                    <div className="fila-titulo">{d.descripcion}</div>
                    <div className="fila-detalle">{fmtFecha(d.fecha)}</div>
                  </div>
                </div>
              ))
            }
          </ContenidoDeLista>
        </Lista>
        <Lista titulo="Recetas" id="histRec">
          <ContenidoDeLista cargando={historia.isLoading} error={historia.error} datos={historia.data?.recetas} vacio="Sin recetas.">
            {(lista) =>
              lista.map((r) => (
                <div className="fila" key={r.id}>
                  <div className="fila-info">
                    <div className="fila-titulo">{r.medicamento}</div>
                    <div className="fila-detalle">
                      {r.indicaciones ?? ""}
                      <br />
                      {fmtFecha(r.fecha)}
                    </div>
                    <EtiquetaDescuento ficha={ficha.data} cobertura={coberturaDe.get(r.id)} error={!!ficha.error} />
                  </div>
                </div>
              ))
            }
          </ContenidoDeLista>
        </Lista>
      </div>
      {/* Cargar diagnósticos y recetas es una acción del profesional. */}
      {!propia && (
        <div className="grid-2 section-gap" id="histForms">
          <NuevoDiagnostico pacienteId={pacienteId} />
          <NuevaReceta pacienteId={pacienteId} />
        </div>
      )}
    </Pantalla>
  );
}

function Ficha({ ficha: f, nombreObraSocial }: { ficha: FichaPaciente; nombreObraSocial: (c?: string | null) => string }) {
  return (
    <>
      <div className="grid-2" style={{ gap: "0 32px" }}>
        <div>
          <Kv label="Nombre">{f.nombre}</Kv>
          <Kv label="Email">{f.email}</Kv>
        </div>
        <div>
          <Kv label="Cobertura">{f.obraSocial ? `${nombreObraSocial(f.obraSocial)} · ${f.planObraSocial ?? ""}` : "Particular (sin obra social)"}</Kv>
          {f.obraSocial && (
            <Kv label="Afiliado">
              {f.numeroAfiliado} · copago {f.copagoPorcentaje ?? 0}%
            </Kv>
          )}
        </div>
      </div>
      {f.avisoCobertura && (
        <p className="inline-hint" style={{ color: "var(--amber-700)" }}>
          {f.avisoCobertura}
        </p>
      )}
    </>
  );
}

/** Descuento de la receta según el vademécum de la obra social del paciente. */
function EtiquetaDescuento({ ficha, cobertura, error }: { ficha?: FichaPaciente; cobertura?: CoberturaReceta; error: boolean }) {
  if (!ficha || error || ficha.avisoCobertura) return null;
  if (!ficha.obraSocial)
    return (
      <span className="badge badge-rol" style={{ marginTop: 6 }}>
        Particular
      </span>
    );
  return cobertura?.cubierto ? (
    <span className="badge badge-AUTORIZADA">
      {ficha.obraSocial}: {cobertura.descuentoPorcentaje}% de descuento
    </span>
  ) : (
    <span className="badge badge-RECHAZADA">Sin descuento en {ficha.obraSocial}</span>
  );
}

function NuevoDiagnostico({ pacienteId }: { pacienteId: number }) {
  const toast = useToast();
  const [descripcion, setDescripcion] = useState("");
  const [registrar] = useRegistrarDiagnosticoMutation();
  const [ejecutar, ocupado] = useAccion();

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (!descripcion.trim()) return void toast("Escribí la descripción", "err");
    void ejecutar(async () => {
      await registrar({ pacienteId, descripcion: descripcion.trim() }).unwrap();
      setDescripcion("");
      toast("Diagnóstico registrado", "ok");
    });
  }

  return (
    <form className="card" onSubmit={enviar} noValidate>
      <h2 className="card-titulo">Nuevo diagnóstico</h2>
      <div className="form-group">
        <label htmlFor="diagDesc">Descripción</label>
        <textarea id="diagDesc" rows={3} placeholder="ej. Hipertensión arterial leve" value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
      </div>
      <BotonAccion ocupado={ocupado} type="submit" id="btnDiag">
        Registrar diagnóstico
      </BotonAccion>
    </form>
  );
}

function NuevaReceta({ pacienteId }: { pacienteId: number }) {
  const toast = useToast();
  const [medicamento, setMedicamento] = useState("");
  const [indicaciones, setIndicaciones] = useState("");
  const [registrar] = useRegistrarRecetaMutation();
  const [ejecutar, ocupado] = useAccion();

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (!medicamento.trim()) return void toast("Escribí el medicamento", "err");
    void ejecutar(async () => {
      await registrar({ pacienteId, medicamento: medicamento.trim(), indicaciones: indicaciones.trim() }).unwrap();
      setMedicamento("");
      setIndicaciones("");
      toast("Receta registrada", "ok");
    });
  }

  return (
    <form className="card" onSubmit={enviar} noValidate>
      <h2 className="card-titulo">Nueva receta</h2>
      <div className="form-group">
        <label htmlFor="recMed">Medicamento</label>
        <input type="text" id="recMed" placeholder="ej. Enalapril 10mg" value={medicamento} onChange={(e) => setMedicamento(e.target.value)} />
      </div>
      <div className="form-group">
        <label htmlFor="recInd">Indicaciones</label>
        <input type="text" id="recInd" placeholder="ej. 1 comprimido por día en ayunas" value={indicaciones} onChange={(e) => setIndicaciones(e.target.value)} />
      </div>
      <BotonAccion ocupado={ocupado} type="submit" id="btnRec">
        Registrar receta
      </BotonAccion>
    </form>
  );
}
