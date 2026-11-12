import { useEffect } from "react";
import { useAppDispatch, useAppSelector } from "../app/hooks";
import { quitarToast, type Toast } from "../features/ui/uiSlice";

export const DURACION_TOAST_MS = 4400;

function UnToast({ toast }: { toast: Toast }) {
  const dispatch = useAppDispatch();
  useEffect(() => {
    const t = setTimeout(() => dispatch(quitarToast(toast.id)), DURACION_TOAST_MS);
    return () => clearTimeout(t);
  }, [dispatch, toast.id]);
  return <div className={"toast " + toast.tipo}>{toast.mensaje}</div>;
}

/** Avisos flotantes. role="status": los lectores de pantalla los leen sin mover el foco. */
export function Toasts() {
  const toasts = useAppSelector((s) => s.ui.toasts);
  return (
    <div id="toast-wrap" role="status" aria-live="polite">
      {toasts.map((t) => (
        <UnToast key={t.id} toast={t} />
      ))}
    </div>
  );
}
