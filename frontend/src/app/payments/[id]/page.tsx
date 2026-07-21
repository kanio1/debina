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
import { PaymentStatusBadge } from "@/components/payments/payment-status-badge";
import { formatAmount, formatTimestamp } from "@/lib/format";
import { AuditDrawer } from "@/components/payments/audit-drawer";

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

interface TimelineEntryResponse {
  seq: number;
  fromStatus: string | null;
  toStatus: string;
  statusCode: string;
  reasonCode: string | null;
  sourceType: string;
  actorType: string;
  isFinal: boolean;
  eventType: string;
  eventRef: string | null;
  at: string;
}

interface TimelineResponse {
  items: TimelineEntryResponse[];
  nextAfterSeq: number | null;
}

type LoadState = "loading" | "error" | "not-found" | "ready";
type TimelineLoadState = "loading" | "error" | "ready";

export default function PaymentDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const [state, setState] = useState<LoadState>("loading");
  const [payment, setPayment] = useState<PaymentDetailResponse | null>(null);
  const [timelineState, setTimelineState] = useState<TimelineLoadState>("loading");
  const [timeline, setTimeline] = useState<TimelineEntryResponse[]>([]);

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

  useEffect(() => {
    let cancelled = false;

    // A failed timeline load never blanks the rest of the detail page (§ Payment Detail /
    // Timeline UI spec: "a failed [tab] does not blank the header or Timeline") — independent
    // load state, independent request, from a separate BFF route.
    async function loadTimeline() {
      try {
        const response = await fetch(`/api/payments/${id}/timeline`, { credentials: "same-origin" });
        if (cancelled) {
          return;
        }
        if (!response.ok) {
          setTimelineState("error");
          return;
        }
        const data = (await response.json()) as TimelineResponse;
        setTimeline(data.items);
        setTimelineState("ready");
      } catch {
        if (!cancelled) {
          setTimelineState("error");
        }
      }
    }

    void loadTimeline();
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
          <CardTitle data-testid="payment.detail.end-to-end-id">
            <h1 className="m-0 text-base leading-snug font-medium">{detail.endToEndId}</h1>
          </CardTitle>
          <AuditDrawer paymentId={detail.id} />
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <div className="text-muted-foreground">Status</div>
            <div data-testid="payment.detail.status">
              <PaymentStatusBadge status={detail.status} />
            </div>
          </div>
          <div>
            <div className="text-muted-foreground">Amount</div>
            <div data-testid="payment.detail.amount" className="tabular-nums">
              {formatAmount(detail.amount)} {detail.currency}
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
          <CardTitle>Timeline</CardTitle>
        </CardHeader>
        <CardContent>
          {timelineState === "loading" && <ScreenState kind="loading" testIdPrefix="payment.detail.timeline" />}
          {timelineState === "error" && (
            <ScreenState kind="error" testIdPrefix="payment.detail.timeline" message="Timeline unavailable." />
          )}
          {timelineState === "ready" && timeline.length === 0 && (
            <ScreenState kind="empty" testIdPrefix="payment.detail.timeline" message="No events yet." />
          )}
          {timelineState === "ready" && timeline.length > 0 && (
            <ol className="flex flex-col gap-3" data-testid="payment.detail.timeline.list">
              {timeline.map((entry) => (
                <li key={entry.seq} data-testid="payment.detail.timeline.entry" className="border-l-2 pl-3">
                  <div className="flex items-center gap-2">
                    <PaymentStatusBadge status={entry.toStatus} />
                    {entry.fromStatus && (
                      <span className="text-muted-foreground text-xs">from {entry.fromStatus}</span>
                    )}
                  </div>
                  <div className="text-muted-foreground text-xs" title={entry.at}>
                    {entry.eventType === "MIGRATION_BASELINE"
                      ? `Initial state imported during migration — observed ${formatTimestamp(entry.at)}`
                      : formatTimestamp(entry.at)}
                  </div>
                  <div className="text-muted-foreground text-xs">
                    Source: {entry.sourceType}
                    {entry.reasonCode && ` · Reason: ${entry.reasonCode}`}
                    {entry.eventRef && (
                      <>
                        {" · "}
                        <span data-testid="payment.detail.timeline.entry.event-ref">{entry.eventRef}</span>
                      </>
                    )}
                  </div>
                </li>
              ))}
            </ol>
          )}
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
