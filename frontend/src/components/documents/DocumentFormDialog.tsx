"use client";

import { useState, type FormEvent } from "react";
import { documentsApi } from "@/lib/api/documents";
import { LIMITS, MEDIA_TYPE_PATTERN, type DocumentSourceType, type Uuid } from "@/lib/api/types";
import { collectErrors, describeSubmitError, withoutError, emptyToNull, maxLength, required, type FieldErrors } from "@/lib/forms";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/fields";
import { FileIcon, LinkIcon } from "@/components/ui/icons";
import { Alert } from "@/components/ui/states";

const SOURCE_TYPES: { value: DocumentSourceType; label: string; hint: string; icon: React.ReactNode }[] = [
  { value: "FILE", label: "File", hint: "PDF, DOCX, image…", icon: <FileIcon /> },
  { value: "URL", label: "Web link", hint: "A page on the web", icon: <LinkIcon /> },
];

interface DocumentFormDialogProps {
  workspaceId: Uuid;
  onClose: () => void;
  onCreated: () => void;
}

/** Registers document metadata. File upload is not part of the API yet. */
export function DocumentFormDialog({ workspaceId, onClose, onCreated }: DocumentFormDialogProps) {
  const [title, setTitle] = useState("");
  const [sourceType, setSourceType] = useState<DocumentSourceType>("FILE");
  const [mediaType, setMediaType] = useState("");
  const [origin, setOrigin] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const clientErrors = collectErrors({
      title: required(title, "Title") ?? maxLength(title, LIMITS.documentTitle, "Title"),
      mediaType:
        maxLength(mediaType, LIMITS.mediaType, "Media type") ??
        (mediaType.trim() && !MEDIA_TYPE_PATTERN.test(mediaType.trim())
          ? "Must be a media type such as application/pdf"
          : undefined),
      origin: maxLength(origin, LIMITS.origin, sourceType === "URL" ? "URL" : "File name"),
    });
    setErrors(clientErrors);
    setFormError(null);
    if (Object.keys(clientErrors).length > 0) return;

    setBusy(true);
    try {
      await documentsApi.create(workspaceId, {
        title: title.trim(),
        sourceType,
        mediaType: emptyToNull(mediaType),
        origin: emptyToNull(origin),
      });
      onCreated();
    } catch (error) {
      const failure = describeSubmitError(error, {
        404: "This workspace no longer exists. It may have been deleted.",
      });
      setErrors(failure.fieldErrors);
      setFormError(failure.formError);
      setBusy(false);
    }
  }

  return (
    <Modal
      title="Add document"
      description="Register a knowledge source's metadata. File upload and ingestion are not available yet."
      onClose={onClose}
      busy={busy}
      onSubmit={submit}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button type="submit" loading={busy}>
            Add document
          </Button>
        </>
      }
    >
      {formError && <Alert>{formError}</Alert>}

      <fieldset>
        <legend className="mb-1.5 text-sm font-medium text-zinc-800 dark:text-zinc-200">Source type</legend>
        <div className="grid grid-cols-2 gap-3">
          {SOURCE_TYPES.map((option) => (
            <label
              key={option.value}
              className={`flex cursor-pointer items-start gap-3 rounded-lg p-3 ring-1 transition has-focus-visible:ring-2 has-focus-visible:ring-indigo-600 ${
                sourceType === option.value
                  ? "bg-indigo-50 ring-indigo-600 dark:bg-indigo-500/10 dark:ring-indigo-500"
                  : "ring-zinc-300 hover:bg-zinc-50 dark:ring-zinc-700 dark:hover:bg-zinc-800"
              }`}
            >
              <input
                type="radio"
                name="sourceType"
                value={option.value}
                checked={sourceType === option.value}
                onChange={() => setSourceType(option.value)}
                className="sr-only"
              />
              <span className="mt-0.5 text-indigo-600 dark:text-indigo-400">{option.icon}</span>
              <span>
                <span className="block text-sm font-medium text-zinc-900 dark:text-zinc-100">{option.label}</span>
                <span className="block text-xs text-zinc-500 dark:text-zinc-400">{option.hint}</span>
              </span>
            </label>
          ))}
        </div>
        {errors.sourceType && (
          <p className="mt-1.5 text-xs font-medium text-red-600 dark:text-red-400">{errors.sourceType}</p>
        )}
      </fieldset>

      <TextField
        label="Title"
        name="title"
        value={title}
        onChange={(e) => { setTitle(e.target.value); setErrors((current) => withoutError(current, "title")); }}
        error={errors.title}
        maxChars={LIMITS.documentTitle}
        placeholder="e.g. Attention Is All You Need"
        autoComplete="off"
      />
      <TextField
        label={sourceType === "URL" ? "URL" : "Original file name"}
        name="origin"
        optional
        value={origin}
        onChange={(e) => { setOrigin(e.target.value); setErrors((current) => withoutError(current, "origin")); }}
        error={errors.origin}
        placeholder={sourceType === "URL" ? "https://arxiv.org/abs/1706.03762" : "attention.pdf"}
        inputMode={sourceType === "URL" ? "url" : undefined}
        autoComplete="off"
      />
      <TextField
        label="Media type"
        name="mediaType"
        optional
        value={mediaType}
        onChange={(e) => { setMediaType(e.target.value); setErrors((current) => withoutError(current, "mediaType")); }}
        error={errors.mediaType}
        placeholder={sourceType === "URL" ? "text/html" : "application/pdf"}
        hint="MIME type of the source, e.g. application/pdf"
        autoComplete="off"
        spellCheck={false}
      />
    </Modal>
  );
}
