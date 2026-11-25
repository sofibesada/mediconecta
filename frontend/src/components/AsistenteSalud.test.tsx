import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { crearStore } from "../app/store";
import { sesionIniciada } from "../features/sesion/sesionSlice";
import { agregar, type Mensaje } from "../lib/asistente";
import { AsistenteSalud } from "./AsistenteSalud";

const AVISO = "Es solo una sugerencia para elegir especialidad; no es un diagnóstico médico.";

function respuesta(status: number, cuerpo: unknown) {
  return new Response(JSON.stringify(cuerpo), { status, headers: { "Content-Type": "application/json" } });
}

/** Muestra a qué ruta se navegó y con qué especialidad. */
function Destino() {
  const loc = useLocation();
  return <p data-testid="destino">{`${loc.pathname} ${(loc.state as { especialidad?: string } | null)?.especialidad ?? ""}`}</p>;
}

function montar() {
  const store = crearStore();
  store.dispatch(sesionIniciada({ id: 7, nombre: "Ana", email: "ana@x.com", rol: "PACIENTE", token: "t" }));
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={["/"]}>
        <AsistenteSalud />
        <Routes>
          <Route path="*" element={<Destino />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
  return userEvent.setup();
}

describe("AsistenteSalud", () => {
  it("arranca cerrado y al abrirlo saluda y pone el foco para escribir", async () => {
    const user = montar();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /qué te pasa/i }));
    expect(screen.getByRole("dialog", { name: "Asistente de salud" })).toBeInTheDocument();
    expect(screen.getByRole("log")).toHaveTextContent("Contame qué te pasa");
    expect(screen.getByLabelText("Contanos qué te pasa")).toHaveFocus();
  });

  it("manda el texto al orientador, muestra la sugerencia y lleva a Agendar con la especialidad", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      respuesta(200, { especialidad: "Dermatología", motivo: "Por la piel.", tipo: "SUGERENCIA", fuente: "IA", aviso: AVISO }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const user = montar();
    await user.click(screen.getByRole("button", { name: /qué te pasa/i }));
    await user.type(screen.getByLabelText("Contanos qué te pasa"), "Me pica la piel{Enter}");

    expect(await screen.findByText("Dermatología")).toBeInTheDocument();
    expect(screen.getByRole("log")).toHaveTextContent("Me pica la piel");
    expect(screen.getByRole("log")).toHaveTextContent("no es un diagnóstico");
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/ia\/orientacion$/);
    expect(JSON.parse(init.body)).toEqual({ sintomas: "Me pica la piel" });
    expect(screen.getByLabelText("Contanos qué te pasa")).toHaveValue("");

    await user.click(screen.getByRole("button", { name: "Sacar turno de Dermatología" }));
    expect(screen.getByTestId("destino")).toHaveTextContent("/agendar Dermatología");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("una urgencia se muestra destacada y sin botón para sacar turno", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      respuesta(200, { especialidad: null, motivo: "Llamá al 107 (SAME) o andá a la guardia.", tipo: "URGENCIA", fuente: "REGLA", aviso: AVISO }),
    ));
    const user = montar();
    await user.click(screen.getByRole("button", { name: /qué te pasa/i }));
    await user.type(screen.getByLabelText("Contanos qué te pasa"), "Me duele el pecho{Enter}");

    expect(await screen.findByText("Atención")).toBeInTheDocument();
    expect(screen.getByRole("log")).toHaveTextContent("107");
    expect(screen.queryByRole("button", { name: /sacar turno/i })).not.toBeInTheDocument();
  });

  it("si el servidor rechaza la consulta, el motivo aparece como respuesta del asistente", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      respuesta(429, { title: "Too Many Requests", detail: "Hiciste muchas consultas seguidas al orientador. Probá de nuevo en 40 segundos." }),
    ));
    const user = montar();
    await user.click(screen.getByRole("button", { name: /qué te pasa/i }));
    await user.type(screen.getByLabelText("Contanos qué te pasa"), "Me duele la rodilla{Enter}");
    expect(await screen.findByText(/muchas consultas seguidas/)).toBeInTheDocument();
  });

  it("no manda un texto vacío y Escape cierra devolviendo el foco al botón", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const user = montar();
    await user.click(screen.getByRole("button", { name: /qué te pasa/i }));
    expect(screen.getByRole("button", { name: "Enviar" })).toBeDisabled();
    await user.type(screen.getByLabelText("Contanos qué te pasa"), "   {Enter}");
    expect(fetchMock).not.toHaveBeenCalled();

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /qué te pasa/i })).toHaveFocus();
  });

  it("conserva solo los últimos 20 mensajes", () => {
    let lista: Mensaje[] = [];
    for (let i = 1; i <= 25; i++) lista = agregar(lista, { id: i, de: "paciente", texto: "m" + i });
    expect(lista).toHaveLength(20);
    expect(lista[0]).toMatchObject({ id: 6 });
    expect(lista[19]).toMatchObject({ id: 25 });
  });
});
