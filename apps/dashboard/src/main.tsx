import React from "react";
import { createRoot } from "react-dom/client";
import "@xyflow/react/dist/style.css";
import App from "./App";
import "./styles.css";

let savedTheme: string | null = null;
try {
  savedTheme = localStorage.getItem("riskgraph-theme");
} catch {
  /* Use the default when storage is unavailable. */
}
document.documentElement.dataset.theme =
  savedTheme === "dark" || savedTheme === "light" ? savedTheme : "dark";

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
