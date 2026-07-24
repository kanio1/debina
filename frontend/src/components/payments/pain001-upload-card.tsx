"use client";

import Link from "next/link";
import { useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { browserRandomUUID } from "@/lib/browser-random-uuid";
import {
  clearIdempotencyBinding,
  invalidateIdempotencyOnPayloadChange,
  parsePain001SubmissionResponse,
  resolveIdempotencyKeyForSubmit,
  type IdempotencyBinding,
} from "@/lib/pain001-upload-submission";
import { frontendPaymentDetailPath } from "@/lib/pain001-upload";
import { readCookie } from "@/lib/read-cookie";

export function Pain001UploadCard() {
  const [selectedFileName, setSelectedFileName] = useState<string | null>(null);
  const [signerId, setSignerId] = useState("");
  const [signatureBase64, setSignatureBase64] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | undefined>();
  const [acceptedPaymentId, setAcceptedPaymentId] = useState<string | null>(null);
  const [acceptedApprovalStatus, setAcceptedApprovalStatus] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const idempotencyBindingRef = useRef<IdempotencyBinding>(clearIdempotencyBinding());
  const xmlBytesRef = useRef<ArrayBuffer | null>(null);

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    setSubmitError(undefined);
    setAcceptedPaymentId(null);
    setAcceptedApprovalStatus(null);
    const file = event.target.files?.[0];
    if (!file) {
      setSelectedFileName(null);
      xmlBytesRef.current = null;
      idempotencyBindingRef.current = clearIdempotencyBinding();
      return;
    }
    const nextBytes = await file.arrayBuffer();
    idempotencyBindingRef.current = invalidateIdempotencyOnPayloadChange(
      idempotencyBindingRef.current,
      nextBytes,
    );
    setSelectedFileName(file.name);
    xmlBytesRef.current = nextBytes;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) {
      return;
    }
    setSubmitError(undefined);
    setAcceptedPaymentId(null);
    setAcceptedApprovalStatus(null);

    if (!xmlBytesRef.current || xmlBytesRef.current.byteLength === 0) {
      setSubmitError("Choose a signed pain.001 XML file before submitting.");
      return;
    }
    if (!signerId.trim() || !signatureBase64.trim()) {
      setSubmitError("Signer identifier and detached signature are required for this channel.");
      return;
    }

    const resolved = resolveIdempotencyKeyForSubmit(
      idempotencyBindingRef.current,
      xmlBytesRef.current,
      browserRandomUUID,
    );
    idempotencyBindingRef.current = resolved.binding;

    setSubmitting(true);
    try {
      const csrfToken = readCookie("sepa_csrf") ?? "";
      const response = await fetch("/api/iso/pain001", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/xml",
          "x-csrf-token": csrfToken,
          "Idempotency-Key": resolved.idempotencyKey,
          "X-Signer-Id": signerId.trim(),
          "X-Signature": signatureBase64.trim(),
          "X-Signature-Algo": "Ed25519",
        },
        body: xmlBytesRef.current,
      });

      const parsed = await parsePain001SubmissionResponse(response);
      if ("error" in parsed) {
        setSubmitError(parsed.error);
        return;
      }

      setAcceptedPaymentId(parsed.paymentId);
      setAcceptedApprovalStatus(parsed.approvalStatus);
      idempotencyBindingRef.current = clearIdempotencyBinding();
      xmlBytesRef.current = null;
      setSelectedFileName(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card className="max-w-lg">
      <CardHeader>
        <CardTitle>Submit signed pain.001</CardTitle>
      </CardHeader>
      <CardContent>
        {submitError && (
          <div
            role="alert"
            data-testid="payments.pain001-upload.error"
            className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {submitError}
          </div>
        )}
        {acceptedPaymentId && (
          <div
            role="status"
            aria-live="polite"
            data-testid="payments.pain001-upload.result"
            className="mb-4 rounded-lg border border-border bg-muted/40 px-3 py-2 text-sm"
          >
            <p data-testid="payments.pain001-upload.result.status">
              Submission accepted
              {acceptedApprovalStatus ? ` (${acceptedApprovalStatus})` : ""}.
            </p>
            <Link
              href={frontendPaymentDetailPath(acceptedPaymentId)}
              data-testid="payments.pain001-upload.result.detail-link"
              className="font-medium text-primary underline underline-offset-2"
            >
              Open payment detail
            </Link>
          </div>
        )}
        <form data-testid="payments.pain001-upload.form" onSubmit={(event) => void handleSubmit(event)} noValidate>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="pain001File">Signed pain.001 file</FieldLabel>
              <Input
                id="pain001File"
                ref={fileInputRef}
                type="file"
                accept=".xml,application/xml,text/xml"
                data-testid="payments.pain001-upload.file-input"
                disabled={submitting}
                onChange={(event) => void handleFileChange(event)}
              />
              {selectedFileName && (
                <p className="text-muted-foreground text-sm" data-testid="payments.pain001-upload.selected-file">
                  Selected: {selectedFileName}
                </p>
              )}
            </Field>
            <Field>
              <FieldLabel htmlFor="signerId">Signer identifier (transport)</FieldLabel>
              <Input
                id="signerId"
                data-testid="payments.pain001-upload.signer-id-input"
                value={signerId}
                disabled={submitting}
                onChange={(event) => setSignerId(event.target.value)}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="signatureBase64">Detached signature (transport)</FieldLabel>
              <Input
                id="signatureBase64"
                data-testid="payments.pain001-upload.signature-input"
                value={signatureBase64}
                disabled={submitting}
                onChange={(event) => setSignatureBase64(event.target.value)}
              />
            </Field>
            <Button type="submit" data-testid="payments.pain001-upload.submit-button" disabled={submitting}>
              {submitting ? "Submitting…" : "Submit pain.001"}
            </Button>
          </FieldGroup>
        </form>
      </CardContent>
    </Card>
  );
}
