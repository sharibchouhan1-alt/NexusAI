"use client";

import { useId, useLayoutEffect, useRef, type FormEvent, type ReactNode } from "react";
import { CloseIcon } from "./icons";

interface ModalProps {
  title: string;
  description?: ReactNode;
  onClose: () => void;
  /** Block Escape / backdrop / close button, e.g. while a request is in flight. */
  busy?: boolean;
  /** When given, the body is a <form> and the footer's submit button submits it. */
  onSubmit?: (event: FormEvent<HTMLFormElement>) => void;
  footer: ReactNode;
  children?: ReactNode;
}

/**
 * Accessible modal built on the native <dialog> element (focus trap, Escape, top layer).
 * Render it only while open. The layout-effect cleanup closes the native dialog when the
 * component unmounts or when Next.js hides the route with <Activity>, so a hidden page
 * never leaves an inert backdrop over the visible one.
 */
export function Modal({ title, description, onClose, busy = false, onSubmit, footer, children }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useLayoutEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (!dialog.open) dialog.showModal();
    // showModal() focuses the first focusable element (the close button); start in the
    // first form field instead. React's autoFocus runs before showModal and is overridden.
    dialog.querySelector<HTMLElement>("input:not([type=radio]), textarea")?.focus();
    return () => dialog.close();
  }, []);

  const requestClose = () => {
    if (!busy) onClose();
  };

  const body = (
    <>
      <div className="flex items-start justify-between gap-4 px-6 pt-5">
        <div>
          <h2 id={titleId} className="text-base font-semibold text-zinc-900 dark:text-zinc-100">{title}</h2>
          {description && (
            <div className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">{description}</div>
          )}
        </div>
        <button
          type="button"
          onClick={requestClose}
          disabled={busy}
          aria-label="Close dialog"
          className="-mr-2 rounded-md p-1.5 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-600 disabled:opacity-50 dark:hover:bg-zinc-800"
        >
          <CloseIcon />
        </button>
      </div>
      {children && <div className="space-y-4 px-6 pt-5">{children}</div>}
      <div className="mt-6 flex flex-col-reverse gap-2 border-t border-zinc-200 bg-zinc-50 px-6 py-4 sm:flex-row sm:justify-end dark:border-zinc-800 dark:bg-zinc-900/60">
        {footer}
      </div>
    </>
  );

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault(); // React state, not the browser, decides when to close.
        requestClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) requestClose(); // backdrop click
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-lg overflow-hidden rounded-xl bg-white p-0 text-left shadow-2xl ring-1 ring-zinc-900/5 backdrop:bg-zinc-950/50 backdrop:backdrop-blur-[2px] dark:bg-zinc-900 dark:ring-zinc-800"
    >
      {onSubmit ? (
        <form noValidate onSubmit={onSubmit}>
          {body}
        </form>
      ) : (
        body
      )}
    </dialog>
  );
}
