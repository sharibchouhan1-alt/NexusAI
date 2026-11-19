"use client";

import { useState, type FormEvent } from "react";
import { workspacesApi } from "@/lib/api/workspaces";
import { LIMITS, type Workspace } from "@/lib/api/types";
import { collectErrors, describeSubmitError, withoutError, emptyToNull, maxLength, required, type FieldErrors } from "@/lib/forms";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { TextAreaField, TextField } from "@/components/ui/fields";
import { Alert } from "@/components/ui/states";

interface WorkspaceFormDialogProps {
  /** Omit to create; pass to rename/edit. */
  workspace?: Workspace;
  onClose: () => void;
  onSaved: (workspace: Workspace) => void;
}

export function WorkspaceFormDialog({ workspace, onClose, onSaved }: WorkspaceFormDialogProps) {
  const editing = workspace !== undefined;
  const [name, setName] = useState(workspace?.name ?? "");
  const [description, setDescription] = useState(workspace?.description ?? "");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const clientErrors = collectErrors({
      name: required(name, "Name") ?? maxLength(name, LIMITS.workspaceName, "Name"),
      description: maxLength(description, LIMITS.workspaceDescription, "Description"),
    });
    setErrors(clientErrors);
    setFormError(null);
    if (Object.keys(clientErrors).length > 0) return;

    setBusy(true);
    const request = { name: name.trim(), description: emptyToNull(description) };
    try {
      const saved = editing
        ? await workspacesApi.update(workspace.id, request)
        : await workspacesApi.create(request);
      onSaved(saved);
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
      title={editing ? "Edit workspace" : "New workspace"}
      description={
        editing
          ? "Rename the workspace or update its description."
          : "Workspaces keep knowledge sources, conversations and research separate."
      }
      onClose={onClose}
      busy={busy}
      onSubmit={submit}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button type="submit" loading={busy}>
            {editing ? "Save changes" : "Create workspace"}
          </Button>
        </>
      }
    >
      {formError && <Alert>{formError}</Alert>}
      <TextField
        label="Name"
        name="name"
        value={name}
        onChange={(e) => { setName(e.target.value); setErrors((current) => withoutError(current, "name")); }}
        error={errors.name}
        maxChars={LIMITS.workspaceName}
        placeholder="e.g. Thesis research"
        autoComplete="off"
      />
      <TextAreaField
        label="Description"
        name="description"
        optional
        value={description}
        onChange={(e) => { setDescription(e.target.value); setErrors((current) => withoutError(current, "description")); }}
        error={errors.description}
        maxChars={LIMITS.workspaceDescription}
        placeholder="What is this workspace for?"
      />
    </Modal>
  );
}
