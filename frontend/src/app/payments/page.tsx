"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { toast } from "sonner";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { PaymentsTable, type PaymentRow, type PaymentsTableStatus } from "@/components/payments/payments-table";
import { ApprovalQueue } from "@/components/payments/approval-queue";
import { browserRandomUUID } from "@/lib/browser-random-uuid";
import { readCookie } from "@/lib/read-cookie";

interface PaymentSummaryResponse {
  id: string;
  endToEndId: string;
  amount: number;
  currency: string;
  status: string | null;
}

interface ProblemDetail {
  title?: string;
  detail?: string;
}

const emptyForm = {
  endToEndId: "",
  amount: "",
  currency: "EUR",
  debtorIban: "",
  creditorIban: "",
};

type FormFieldErrors = Partial<Record<keyof typeof emptyForm, string>>;

const FIELD_ORDER: (keyof typeof emptyForm)[] = ["endToEndId", "amount", "currency", "debtorIban", "creditorIban"];

const IBAN_PATTERN = /^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$/;

function validateForm(form: typeof emptyForm): FormFieldErrors {
  const errors: FormFieldErrors = {};

  if (!form.endToEndId.trim()) {
    errors.endToEndId = "End-to-end ID is required.";
  }
  if (!form.amount || Number.isNaN(Number(form.amount)) || Number(form.amount) <= 0) {
    errors.amount = "Enter an amount greater than 0.";
  }
  if (!/^[A-Z]{3}$/.test(form.currency)) {
    errors.currency = "Currency must be a 3-letter ISO code, e.g. EUR.";
  }
  if (!IBAN_PATTERN.test(form.debtorIban)) {
    errors.debtorIban = "Enter a valid IBAN, e.g. DE89370400440532013000.";
  }
  if (!IBAN_PATTERN.test(form.creditorIban)) {
    errors.creditorIban = "Enter a valid IBAN, e.g. DE89370400440532013000.";
  }

  return errors;
}

export default function PaymentsPage() {
  const [listStatus, setListStatus] = useState<PaymentsTableStatus>("loading");
  const [payments, setPayments] = useState<PaymentRow[]>([]);
  const [listError, setListError] = useState<string | undefined>();
  const [form, setForm] = useState(emptyForm);
  const [fieldErrors, setFieldErrors] = useState<FormFieldErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | undefined>();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const errorSummaryRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (Object.keys(fieldErrors).length > 0) {
      errorSummaryRef.current?.focus();
    }
  }, [fieldErrors]);

  async function loadPayments() {
    try {
      const response = await fetch("/api/payments", { credentials: "same-origin" });
      if (!response.ok) {
        throw new Error(`Failed to load payments (${response.status})`);
      }
      const data = (await response.json()) as PaymentSummaryResponse[];
      setPayments(data);
      setListStatus("ready");
    } catch (error) {
      setListError(error instanceof Error ? error.message : "Could not load payments.");
      setListStatus("error");
    }
  }

  useEffect(() => {
    // Canonical fetch-on-mount pattern; the setState calls happen after the
    // `await fetch(...)` above, not synchronously in this effect body.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadPayments();
  }, []);

  function handleFormSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(undefined);
    const errors = validateForm(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setConfirmOpen(true);
  }

  async function confirmSubmit() {
    setSubmitting(true);
    try {
      const csrfToken = readCookie("sepa_csrf") ?? "";
      const response = await fetch("/api/payments", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json",
          "x-csrf-token": csrfToken,
          "Idempotency-Key": browserRandomUUID(),
        },
        body: JSON.stringify({
          endToEndId: form.endToEndId,
          amount: Number(form.amount),
          currency: form.currency,
          debtorIban: form.debtorIban,
          creditorIban: form.creditorIban,
        }),
      });

      if (!response.ok) {
        const problem = (await response.json().catch(() => null)) as ProblemDetail | null;
        setSubmitError(problem?.detail ?? problem?.title ?? `Payment submission failed (${response.status})`);
        return;
      }

      toast.success("Payment submitted.");
      setForm(emptyForm);
      await loadPayments();
    } finally {
      setSubmitting(false);
      setConfirmOpen(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-lg font-semibold text-foreground">Payments &amp; Files</h1>
      <Card className="max-w-lg">
        <CardHeader>
          <CardTitle>Submit payment</CardTitle>
        </CardHeader>
        <CardContent>
          {submitError && (
            <div
              role="alert"
              data-testid="payments.submit.error"
              className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive"
            >
              {submitError}
            </div>
          )}
          {Object.keys(fieldErrors).length > 0 && (
            <div
              ref={errorSummaryRef}
              role="alert"
              aria-labelledby="payment-form-error-summary-title"
              tabIndex={-1}
              data-testid="payments.submit.error-summary"
              className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm outline-none"
            >
              <h2 id="payment-form-error-summary-title" className="font-medium text-destructive">
                Correct the following errors
              </h2>
              <ul className="mt-2 list-disc space-y-1 pl-5">
                {FIELD_ORDER.filter((field) => fieldErrors[field]).map((field) => (
                  <li key={field}>
                    <a
                      href={`#${field}`}
                      data-testid={`payments.submit.error-summary.${field}-link`}
                      className="text-destructive underline underline-offset-2 hover:text-destructive/80"
                    >
                      {fieldErrors[field]}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          )}
          <form data-testid="payments.submit.form" onSubmit={handleFormSubmit} noValidate>
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="endToEndId">End-to-end ID</FieldLabel>
                <Input
                  id="endToEndId"
                  data-testid="payments.submit.end-to-end-id-input"
                  required
                  aria-invalid={Boolean(fieldErrors.endToEndId)}
                  aria-describedby={fieldErrors.endToEndId ? "endToEndId-error" : undefined}
                  value={form.endToEndId}
                  onChange={(event) => setForm((prev) => ({ ...prev, endToEndId: event.target.value }))}
                />
                {fieldErrors.endToEndId && (
                  <FieldError id="endToEndId-error" data-testid="payments.submit.end-to-end-id-error">
                    {fieldErrors.endToEndId}
                  </FieldError>
                )}
              </Field>
              <Field>
                <FieldLabel htmlFor="amount">Amount</FieldLabel>
                <Input
                  id="amount"
                  type="number"
                  step="0.01"
                  min="0.01"
                  data-testid="payments.submit.amount-input"
                  required
                  aria-invalid={Boolean(fieldErrors.amount)}
                  aria-describedby={fieldErrors.amount ? "amount-error" : undefined}
                  value={form.amount}
                  onChange={(event) => setForm((prev) => ({ ...prev, amount: event.target.value }))}
                />
                {fieldErrors.amount && (
                  <FieldError id="amount-error" data-testid="payments.submit.amount-error">
                    {fieldErrors.amount}
                  </FieldError>
                )}
              </Field>
              <Field>
                <FieldLabel htmlFor="currency">Currency</FieldLabel>
                <Input
                  id="currency"
                  data-testid="payments.submit.currency-input"
                  required
                  maxLength={3}
                  aria-invalid={Boolean(fieldErrors.currency)}
                  aria-describedby={fieldErrors.currency ? "currency-error" : undefined}
                  value={form.currency}
                  onChange={(event) =>
                    setForm((prev) => ({ ...prev, currency: event.target.value.toUpperCase() }))
                  }
                />
                {fieldErrors.currency && (
                  <FieldError id="currency-error" data-testid="payments.submit.currency-error">
                    {fieldErrors.currency}
                  </FieldError>
                )}
              </Field>
              <Field>
                <FieldLabel htmlFor="debtorIban">Debtor IBAN</FieldLabel>
                <Input
                  id="debtorIban"
                  data-testid="payments.submit.debtor-iban-input"
                  required
                  aria-invalid={Boolean(fieldErrors.debtorIban)}
                  aria-describedby={fieldErrors.debtorIban ? "debtorIban-error" : undefined}
                  value={form.debtorIban}
                  onChange={(event) => setForm((prev) => ({ ...prev, debtorIban: event.target.value }))}
                />
                {fieldErrors.debtorIban && (
                  <FieldError id="debtorIban-error" data-testid="payments.submit.debtor-iban-error">
                    {fieldErrors.debtorIban}
                  </FieldError>
                )}
              </Field>
              <Field>
                <FieldLabel htmlFor="creditorIban">Creditor IBAN</FieldLabel>
                <Input
                  id="creditorIban"
                  data-testid="payments.submit.creditor-iban-input"
                  required
                  aria-invalid={Boolean(fieldErrors.creditorIban)}
                  aria-describedby={fieldErrors.creditorIban ? "creditorIban-error" : undefined}
                  value={form.creditorIban}
                  onChange={(event) => setForm((prev) => ({ ...prev, creditorIban: event.target.value }))}
                />
                {fieldErrors.creditorIban && (
                  <FieldError id="creditorIban-error" data-testid="payments.submit.creditor-iban-error">
                    {fieldErrors.creditorIban}
                  </FieldError>
                )}
              </Field>
              <Button type="submit" data-testid="payments.submit.submit-button" disabled={submitting}>
                {submitting ? "Submitting…" : "Submit payment"}
              </Button>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>

      <AlertDialog open={confirmOpen} onOpenChange={(open) => !submitting && setConfirmOpen(open)}>
        <AlertDialogContent data-testid="payments.submit.confirm-dialog">
          <AlertDialogHeader>
            <AlertDialogTitle>Confirm payment submission</AlertDialogTitle>
            <AlertDialogDescription>
              You are about to submit {form.amount} {form.currency} from {form.debtorIban} to{" "}
              {form.creditorIban} (end-to-end ID {form.endToEndId}). This cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel data-testid="payments.submit.confirm-dialog.cancel-button" disabled={submitting}>
              Cancel
            </AlertDialogCancel>
            <AlertDialogAction
              data-testid="payments.submit.confirm-dialog.confirm-button"
              disabled={submitting}
              onClick={() => void confirmSubmit()}
            >
              {submitting ? "Submitting…" : "Confirm & submit"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <PaymentsTable status={listStatus} payments={payments} errorMessage={listError} />
      <ApprovalQueue />
    </div>
  );
}
