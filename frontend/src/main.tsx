import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { Provider } from "react-redux";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { crearStoreDeLaApp } from "./app/store";
import { escucharActividad } from "./features/sesion/token";
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
