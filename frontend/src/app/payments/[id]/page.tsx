"use client";

import { use, useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ScreenState } from "@/components/shared/screen-state";

interface IsoIdentifierResponse {
  sourceMessageType: string;
  endToEndId: string;
  isoMessageId: string;
}

interface PaymentDetailResponse {
  id: string;
  endToEndId: string;
  amount: number;
  currency: string;
  status: string;
  debtorIban: string;
  creditorIban: string;
  isoIdentifiers: IsoIdentifierResponse[];
}

type LoadState = "loading" | "error" | "not-found" | "ready";

export default function PaymentDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const [state, setState] = useState<LoadState>("loading");
  const [payment, setPayment] = useState<PaymentDetailResponse | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const response = await fetch(`/api/payments/${id}`, { credentials: "same-origin" });
        if (cancelled) {
          return;
        }
        if (response.status === 404) {
          setState("not-found");
          return;
        }
        if (!response.ok) {
          setState("error");
          return;
        }
        const data = (await response.json()) as PaymentDetailResponse;
        setPayment(data);
        setState("ready");
      } catch {
        if (!cancelled) {
          setState("error");
        }
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (state === "loading") {
    return <ScreenState kind="loading" testIdPrefix="payment.detail" />;
  }
  if (state === "error") {
    return <ScreenState kind="error" testIdPrefix="payment.detail" message="Could not load this payment." />;
  }
  if (state === "not-found") {
    return <ScreenState kind="empty" testIdPrefix="payment.detail" message="This payment does not exist." />;
  }

  const detail = payment as PaymentDetailResponse;

  return (
    <div className="flex flex-col gap-6" data-testid="payment.detail.page">
      <Card>
        <CardHeader>
          <CardTitle data-testid="payment.detail.end-to-end-id">{detail.endToEndId}</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <div className="text-muted-foreground">Status</div>
            <div data-testid="payment.detail.status">{detail.status}</div>
          </div>
          <div>
            <div className="text-muted-foreground">Amount</div>
            <div data-testid="payment.detail.amount">
              {detail.amount} {detail.currency}
            </div>
          </div>
          <div>
            <div className="text-muted-foreground">Debtor IBAN</div>
            <div data-testid="payment.detail.debtor-iban">{detail.debtorIban}</div>
          </div>
          <div>
            <div className="text-muted-foreground">Creditor IBAN</div>
            <div data-testid="payment.detail.creditor-iban">{detail.creditorIban}</div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>ISO identifiers</CardTitle>
        </CardHeader>
        <CardContent>
          <Table data-testid="payment.detail.iso-identifiers.table">
            <TableHeader>
              <TableRow>
                <TableHead scope="col">Source message type</TableHead>
                <TableHead scope="col">End-to-end ID</TableHead>
                <TableHead scope="col">ISO message ID</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {detail.isoIdentifiers.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={3}>
                    <ScreenState kind="empty" testIdPrefix="payment.detail.iso-identifiers" message="No ISO identifiers recorded yet." />
                  </TableCell>
                </TableRow>
              ) : (
                detail.isoIdentifiers.map((identifier) => (
                  <TableRow key={identifier.isoMessageId} data-testid="payment.detail.iso-identifiers.row">
                    <TableCell>{identifier.sourceMessageType}</TableCell>
                    <TableCell>{identifier.endToEndId}</TableCell>
                    <TableCell>{identifier.isoMessageId}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
