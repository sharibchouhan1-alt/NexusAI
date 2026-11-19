"use client";

import { useId, useRef, useState, type DragEvent, type FormEvent } from "react";
import { isAbortError, toApiError } from "@/lib/api/client";
import { documentsApi } from "@/lib/api/documents";
import { LIMITS, UPLOAD_EXTENSIONS, type DocumentMetadata, type Uuid } from "@/lib/api/types";
import { formatBytes } from "@/lib/format";
import { describeSubmitError, maxLength, withoutError, type FieldErrors } from "@/lib/forms";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/fields";
import { FileIcon } from "@/components/ui/icons";
import { Alert } from "@/components/ui/states";

interface UploadDialogProps {
  workspaceId: Uuid;
  onClose: () => void;
  onUploaded: (document: DocumentMetadata) => void;
}

/** Client-side pre-checks; the server re-validates name, content and size. */
function validateFile(file: File): string | undefined {
  const name = file.name.toLowerCase();
  if (!UPLOAD_EXTENSIONS.some((ext) => name.endsWith(ext))) return "Only PDF (.pdf) and plain text (.txt) files are supported";
  if (file.size === 0) return "The file is empty";
  if (file.size > LIMITS.uploadMaxBytes) return `The file is larger than ${formatBytes(LIMITS.uploadMaxBytes)}`;
  return undefined;
}

export function UploadDialog({ workspaceId, onClose, onUploaded }: UploadDialogProps) {
  const inputId = useId();
  const abortRef = useRef<AbortController | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [dragging, setDragging] = useState(false);
  const uploading = progress !== null;

  function choose(next: File | undefined) {
    if (!next) return;
    setFile(next);
    setFormError(null);
    const error = validateFile(next);
    setErrors((current) => (error ? { ...current, file: error } : withoutError(current, "file")));
  }

  function onDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault();
    setDragging(false);
    if (!uploading) choose(event.dataTransfer.files[0]);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fileError = file ? validateFile(file) : "Choose a file to upload";
    const titleError = maxLength(title, LIMITS.documentTitle, "Title");
    const clientErrors: FieldErrors = {
      ...(fileError && { file: fileError }),
      ...(titleError && { title: titleError }),
    };
    setErrors(clientErrors);
    setFormError(null);
    if (!file || Object.keys(clientErrors).length > 0) return;

    const controller = new AbortController();
    abortRef.current = controller;
    setProgress(0);
    try {
      const document = await documentsApi.upload(workspaceId, file, title.trim() || null, {
        signal: controller.signal,
        onProgress: setProgress,
      });
      onUploaded(document);
    } catch (error) {
      setProgress(null);
      if (isAbortError(error)) {
        setFormError("Upload cancelled.");
        return;
      }
      const failure = describeSubmitError(error, {
        404: "This workspace no longer exists. It may have been deleted.",
      });
      // File-level problems (empty, wrong type, too large) belong next to the file picker.
      const fileLevel = failure.formError !== null && [400, 413, 415].includes(toApiError(error).status);
      setErrors(fileLevel ? { file: failure.formError! } : failure.fieldErrors);
      setFormError(fileLevel ? null : failure.formError);
    } finally {
      abortRef.current = null;
    }
  }

  const percent = Math.round((progress ?? 0) * 100);

  return (
    <Modal
      title="Upload document"
      description="PDF or plain-text (UTF-8) files. Text is extracted and split into chunks for retrieval."
      onClose={onClose}
      busy={uploading}
      onSubmit={submit}
      footer={
        uploading ? (
          <Button variant="secondary" onClick={() => abortRef.current?.abort()}>
            Cancel upload
          </Button>
        ) : (
          <>
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={!file || Boolean(errors.file)}>
              Upload
            </Button>
          </>
        )
      }
    >
      {formError && <Alert>{formError}</Alert>}

      <div>
        <label
          htmlFor={inputId}
          onDragOver={(e) => {
            e.preventDefault();
            if (!uploading) setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
          className={`flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed px-6 py-8 text-center transition-colors has-focus-visible:ring-2 has-focus-visible:ring-indigo-600 ${
            errors.file
              ? "border-red-300 bg-red-50/40 dark:border-red-800 dark:bg-red-950/20"
              : dragging
                ? "border-indigo-500 bg-indigo-50 dark:bg-indigo-500/10"
                : "border-zinc-300 hover:border-indigo-400 hover:bg-zinc-50 dark:border-zinc-700 dark:hover:bg-zinc-800/50"
          } ${uploading ? "pointer-events-none opacity-70" : ""}`}
        >
          <span className="mb-3 flex size-10 items-center justify-center rounded-full bg-indigo-50 text-indigo-600 dark:bg-indigo-500/10 dark:text-indigo-400">
            <FileIcon className="size-5" />
          </span>
          {file ? (
            <>
              <span className="max-w-full truncate text-sm font-medium text-zinc-900 dark:text-zinc-100">{file.name}</span>
              <span className="mt-0.5 text-xs text-zinc-500">{formatBytes(file.size)} · click or drop to replace</span>
            </>
          ) : (
            <>
              <span className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                Drop a file here, or <span className="text-indigo-600 dark:text-indigo-400">browse</span>
              </span>
              <span className="mt-0.5 text-xs text-zinc-500">
                PDF or TXT · up to {formatBytes(LIMITS.uploadMaxBytes)}
              </span>
            </>
          )}
          <input
            id={inputId}
            type="file"
            name="file"
            accept=".pdf,.txt,application/pdf,text/plain"
            className="sr-only"
            disabled={uploading}
            aria-invalid={errors.file ? true : undefined}
            aria-describedby={errors.file ? `${inputId}-error` : undefined}
            onChange={(e) => {
              choose(e.target.files?.[0]);
              e.target.value = ""; // allow re-selecting the same file
            }}
          />
        </label>
        {errors.file && (
          <p id={`${inputId}-error`} className="mt-1.5 text-xs font-medium text-red-600 dark:text-red-400">
            {errors.file}
          </p>
        )}
      </div>

      <TextField
        label="Title"
        name="title"
        optional
        value={title}
        disabled={uploading}
        onChange={(e) => {
          setTitle(e.target.value);
          setErrors((current) => withoutError(current, "title"));
        }}
        error={errors.title}
        maxChars={LIMITS.documentTitle}
        hint="Defaults to the file name"
        autoComplete="off"
      />

      {uploading && (
        <div role="status" aria-live="polite">
          <div className="mb-1.5 flex justify-between text-xs text-zinc-600 dark:text-zinc-400">
            <span>{percent < 100 ? "Uploading…" : "Upload complete, saving…"}</span>
            <span className="tabular-nums">{percent}%</span>
          </div>
          <div
            className="h-2 overflow-hidden rounded-full bg-zinc-200 dark:bg-zinc-800"
            role="progressbar"
            aria-label="Upload progress"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={percent}
          >
            <div className="h-full rounded-full bg-indigo-600 transition-[width] duration-200" style={{ width: `${percent}%` }} />
          </div>
        </div>
      )}
    </Modal>
  );
}
