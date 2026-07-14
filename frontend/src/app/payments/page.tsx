"use client";

import { useEffect, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { PaymentsTable, type PaymentRow, type PaymentsTableStatus } from "@/components/payments/payments-table";
import { readCookie } from "@/lib/read-cookie";

interface PaymentSummaryResponse {
  id: string;
  endToEndId: string;
  amount: number;
  currency: string;
  status: string;
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

export default function PaymentsPage() {
  const [listStatus, setListStatus] = useState<PaymentsTableStatus>("loading");
  const [payments, setPayments] = useState<PaymentRow[]>([]);
  const [listError, setListError] = useState<string | undefined>();
  const [form, setForm] = useState(emptyForm);
  const [submitting, setSubmitting] = useState(false);

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

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    try {
      const csrfToken = readCookie("sepa_csrf") ?? "";
      const response = await fetch("/api/payments", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json",
          "x-csrf-token": csrfToken,
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
        toast.error(problem?.detail ?? problem?.title ?? `Payment submission failed (${response.status})`);
        return;
      }

      toast.success("Payment submitted.");
      setForm(emptyForm);
      await loadPayments();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>Submit payment</CardTitle>
        </CardHeader>
        <CardContent>
          <form data-testid="payments.submit.form" onSubmit={handleSubmit}>
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="endToEndId">End-to-end ID</FieldLabel>
                <Input
                  id="endToEndId"
                  data-testid="payments.submit.end-to-end-id-input"
                  required
                  value={form.endToEndId}
                  onChange={(event) => setForm((prev) => ({ ...prev, endToEndId: event.target.value }))}
                />
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
                  value={form.amount}
                  onChange={(event) => setForm((prev) => ({ ...prev, amount: event.target.value }))}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="currency">Currency</FieldLabel>
                <Input
                  id="currency"
                  data-testid="payments.submit.currency-input"
                  required
                  maxLength={3}
                  value={form.currency}
                  onChange={(event) =>
                    setForm((prev) => ({ ...prev, currency: event.target.value.toUpperCase() }))
                  }
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="debtorIban">Debtor IBAN</FieldLabel>
                <Input
                  id="debtorIban"
                  data-testid="payments.submit.debtor-iban-input"
                  required
                  value={form.debtorIban}
                  onChange={(event) => setForm((prev) => ({ ...prev, debtorIban: event.target.value }))}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="creditorIban">Creditor IBAN</FieldLabel>
                <Input
                  id="creditorIban"
                  data-testid="payments.submit.creditor-iban-input"
                  required
                  value={form.creditorIban}
                  onChange={(event) => setForm((prev) => ({ ...prev, creditorIban: event.target.value }))}
                />
              </Field>
              <Button type="submit" data-testid="payments.submit.submit-button" disabled={submitting}>
                {submitting ? "Submitting…" : "Submit payment"}
              </Button>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>

      <PaymentsTable status={listStatus} payments={payments} errorMessage={listError} />
    </div>
  );
}
