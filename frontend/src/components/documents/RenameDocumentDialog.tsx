"use client";

import { useState, type FormEvent } from "react";
import { documentsApi } from "@/lib/api/documents";
import { LIMITS, type DocumentMetadata } from "@/lib/api/types";
import { collectErrors, describeSubmitError, withoutError, maxLength, required, type FieldErrors } from "@/lib/forms";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/fields";
import { Alert } from "@/components/ui/states";

interface RenameDocumentDialogProps {
  document: DocumentMetadata;
  onClose: () => void;
  onSaved: () => void;
}

export function RenameDocumentDialog({ document, onClose, onSaved }: RenameDocumentDialogProps) {
  const [title, setTitle] = useState(document.title);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const clientErrors = collectErrors({
      title: required(title, "Title") ?? maxLength(title, LIMITS.documentTitle, "Title"),
    });
    setErrors(clientErrors);
    setFormError(null);
    if (Object.keys(clientErrors).length > 0) return;

    setBusy(true);
    try {
      await documentsApi.update(document.workspaceId, document.id, { title: title.trim() });
      onSaved();
    } catch (error) {
      const failure = describeSubmitError(error, {
        404: "This document no longer exists. It may have been deleted.",
      });
      setErrors(failure.fieldErrors);
      setFormError(failure.formError);
      setBusy(false);
    }
  }

  return (
    <Modal
      title="Rename document"
      description="Source type and origin identify the source and can't be changed."
      onClose={onClose}
      busy={busy}
      onSubmit={submit}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button type="submit" loading={busy}>
            Save
          </Button>
        </>
      }
    >
      {formError && <Alert>{formError}</Alert>}
      <TextField
        label="Title"
        name="title"
        value={title}
        onChange={(e) => { setTitle(e.target.value); setErrors((current) => withoutError(current, "title")); }}
        error={errors.title}
        maxChars={LIMITS.documentTitle}
        autoComplete="off"
      />
    </Modal>
  );
}
