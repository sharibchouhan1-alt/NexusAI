import { toApiError } from "@/lib/api/client";

export type FieldErrors = Record<string, string>;

export interface SubmitFailure {
  fieldErrors: FieldErrors;
  formError: string | null;
}

/** Splits an API failure into per-field messages (400) and a form-level message. */
export function describeSubmitError(error: unknown, messages: Partial<Record<number, string>> = {}): SubmitFailure {
  const apiError = toApiError(error);
  const fieldErrors = apiError.fieldErrors;
  if (Object.keys(fieldErrors).length > 0) {
    return { fieldErrors, formError: null };
  }
  return { fieldErrors: {}, formError: messages[apiError.status] ?? apiError.message };
}

export function required(value: string, label: string): string | undefined {
  return value.trim() ? undefined : `${label} is required`;
}

export function maxLength(value: string, max: number, label: string): string | undefined {
  return value.trim().length > max ? `${label} must be at most ${max} characters` : undefined;
}

/** Drops undefined entries so `Object.keys(errors).length` reflects real errors. */
export function collectErrors(errors: Record<string, string | undefined>): FieldErrors {
  return Object.fromEntries(
    Object.entries(errors).filter((entry): entry is [string, string] => entry[1] !== undefined),
  );
}

/** Returns a copy without `field`, to clear a field's error as soon as the user edits it. */
export function withoutError(errors: FieldErrors, field: string): FieldErrors {
  if (!(field in errors)) return errors;
  const next = { ...errors };
  delete next[field];
  return next;
}

export const emptyToNull = (value: string) => (value.trim() ? value.trim() : null);
