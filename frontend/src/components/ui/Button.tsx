import type { ButtonHTMLAttributes, ReactNode } from "react";
import { Spinner } from "./states";

type Variant = "primary" | "secondary" | "danger" | "ghost";
type Size = "sm" | "md";

const variants: Record<Variant, string> = {
  primary:
    "bg-indigo-600 text-white shadow-sm hover:bg-indigo-500 focus-visible:outline-indigo-600 dark:bg-indigo-500 dark:hover:bg-indigo-400",
  secondary:
    "bg-white text-zinc-900 shadow-sm ring-1 ring-inset ring-zinc-300 hover:bg-zinc-50 focus-visible:outline-indigo-600 dark:bg-zinc-800 dark:text-zinc-100 dark:ring-zinc-700 dark:hover:bg-zinc-700",
  danger:
    "bg-red-600 text-white shadow-sm hover:bg-red-500 focus-visible:outline-red-600",
  ghost:
    "text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900 focus-visible:outline-indigo-600 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100",
};

const sizes: Record<Size, string> = {
  sm: "h-8 gap-1.5 px-2.5 text-sm",
  md: "h-9 gap-2 px-3.5 text-sm",
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  icon?: ReactNode;
}

export function Button({
  variant = "primary",
  size = "md",
  loading = false,
  icon,
  disabled,
  className = "",
  children,
  type = "button",
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`inline-flex shrink-0 items-center justify-center rounded-md font-medium whitespace-nowrap transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed disabled:opacity-60 ${variants[variant]} ${sizes[size]} ${className}`}
      {...props}
    >
      {loading ? <Spinner className="size-4" /> : icon}
      {children}
    </button>
  );
}
