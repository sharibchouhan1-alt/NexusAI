"use client";

import { useState, type ReactNode } from "react";
import { Button } from "./Button";
import { Modal } from "./Modal";
import { Alert } from "./states";

interface ConfirmDialogProps {
  title: string;
  message: ReactNode;
  confirmLabel: string;
  /** Runs the action. Throw to keep the dialog open; the message is shown inline. */
  onConfirm: () => Promise<void>;
  onClose: () => void;
  /** Turns a thrown error into a user-facing message. */
  describeError?: (error: unknown) => string;
}

export function ConfirmDialog({
  title,
  message,
  confirmLabel,
  onConfirm,
  onClose,
  describeError = (e) => (e instanceof Error ? e.message : "The action failed"),
}: ConfirmDialogProps) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      await onConfirm();
    } catch (e) {
      setError(describeError(e));
      setBusy(false);
    }
  }

  return (
    <Modal
      title={title}
      description={message}
      onClose={onClose}
      busy={busy}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button variant="danger" onClick={confirm} loading={busy}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      {error && <Alert>{error}</Alert>}
    </Modal>
  );
}
