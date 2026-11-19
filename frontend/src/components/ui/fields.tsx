import { useId, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from "react";

const control =
  "block w-full rounded-md border-0 bg-white px-3 py-2 text-sm text-zinc-900 shadow-sm ring-1 ring-inset placeholder:text-zinc-400 focus:ring-2 focus:ring-inset focus:outline-none disabled:opacity-60 dark:bg-zinc-900 dark:text-zinc-100 dark:placeholder:text-zinc-500";
const ok = "ring-zinc-300 focus:ring-indigo-600 dark:ring-zinc-700 dark:focus:ring-indigo-500";
const bad = "ring-red-400 focus:ring-red-500 dark:ring-red-700";

interface FieldShellProps {
  label: string;
  error?: string;
  hint?: ReactNode;
  optional?: boolean;
  counter?: { length: number; max: number };
  children: (ids: { id: string; describedBy: string | undefined }) => ReactNode;
}

function FieldShell({ label, error, hint, optional, counter, children }: FieldShellProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint && hintId, error && errorId].filter(Boolean).join(" ") || undefined;
  const over = counter && counter.length > counter.max;

  return (
    <div>
      <div className="mb-1.5 flex items-baseline justify-between gap-2">
        <label htmlFor={id} className="text-sm font-medium text-zinc-800 dark:text-zinc-200">
          {label}
          {optional && <span className="ml-1 font-normal text-zinc-400">(optional)</span>}
        </label>
        {counter && (
          <span className={`text-xs tabular-nums ${over ? "text-red-600" : "text-zinc-400"}`}>
            {counter.length}/{counter.max}
          </span>
        )}
      </div>
      {children({ id, describedBy })}
      {hint && !error && (
        <p id={hintId} className="mt-1.5 text-xs text-zinc-500 dark:text-zinc-400">{hint}</p>
      )}
      {error && (
        <p id={errorId} className="mt-1.5 text-xs font-medium text-red-600 dark:text-red-400">{error}</p>
      )}
    </div>
  );
}

type TextFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, "id"> &
  Omit<FieldShellProps, "children" | "counter"> & { maxChars?: number };

export function TextField({ label, error, hint, optional, maxChars, ...input }: TextFieldProps) {
  const length = String(input.value ?? "").trim().length;
  return (
    <FieldShell
      label={label}
      error={error}
      hint={hint}
      optional={optional}
      counter={maxChars ? { length, max: maxChars } : undefined}
    >
      {({ id, describedBy }) => (
        <input
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={`${control} ${error ? bad : ok}`}
          {...input}
        />
      )}
    </FieldShell>
  );
}

type TextAreaFieldProps = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, "id"> &
  Omit<FieldShellProps, "children" | "counter"> & { maxChars?: number };

export function TextAreaField({ label, error, hint, optional, maxChars, ...textarea }: TextAreaFieldProps) {
  const length = String(textarea.value ?? "").trim().length;
  return (
    <FieldShell
      label={label}
      error={error}
      hint={hint}
      optional={optional}
      counter={maxChars ? { length, max: maxChars } : undefined}
    >
      {({ id, describedBy }) => (
        <textarea
          id={id}
          rows={3}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={`${control} resize-y ${error ? bad : ok}`}
          {...textarea}
        />
      )}
    </FieldShell>
  );
}
