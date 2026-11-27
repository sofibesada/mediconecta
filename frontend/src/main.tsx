import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { Provider } from "react-redux";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { crearStoreDeLaApp } from "./app/store";
import { escucharActividad } from "./features/sesion/token";
// Fuentes servidas por la propia app (sin Google Fonts): sin pedidos a terceros y con una CSP
// que solo permite 'self'.
import "@fontsource/inter/latin-300.css";
import "@fontsource/inter/latin-400.css";
import "@fontsource/inter/latin-500.css";
import "@fontsource/inter/latin-600.css";
import "@fontsource/inter/latin-700.css";
import "@fontsource/lora/latin-400.css";
import "@fontsource/lora/latin-500.css";
import "@fontsource/lora/latin-600.css";
import "@fontsource/lora/latin-700.css";
import "./styles/global.css";

// Los clics y las teclas cuentan como actividad para renovar el token de la sesión.
escucharActividad();

const store = crearStoreDeLaApp();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Provider store={store}>
      <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <App />
      </BrowserRouter>
    </Provider>
  </StrictMode>,
);
