"use client";

import { useCallback, useEffect, useState } from "react";
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
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import type { ApprovalQueueQuery } from "@/generated/graphql";
import { readCookie } from "@/lib/read-cookie";
import { browserRandomUUID } from "@/lib/browser-random-uuid";

const PAGE_SIZE = 25;

type ApprovalItem = ApprovalQueueQuery["approvalQueue"]["items"][number];
type QueueState = "loading" | "ready" | "empty" | "unauthorized" | "error";

interface GraphQlResponse<T> {
  data?: T;
  errors?: Array<{ message?: string }>;
}

interface SessionResponse {
  claims: { sub: string; roles: string[] };
}

interface ProblemDetail {
  title?: string;
  detail?: string;
}

function approvalStatus(item: ApprovalItem) {
  return item.expiredButUnprocessed ? "Expired — awaiting processing" : item.approvalStatus;
}

function describeFailure(response: Response, problem: ProblemDetail | null) {
  return problem?.detail ?? problem?.title ?? `Approval decision failed (${response.status}).`;
}

export function ApprovalQueue() {
  const [state, setState] = useState<QueueState>("loading");
  const [items, setItems] = useState<ApprovalItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [error, setError] = useState<string>();
  const [currentUserId, setCurrentUserId] = useState<string>();
  const [approveTarget, setApproveTarget] = useState<ApprovalItem>();
  const [rejectTarget, setRejectTarget] = useState<ApprovalItem>();
  const [rejectComment, setRejectComment] = useState("");
  const [decisionInFlight, setDecisionInFlight] = useState(false);

  const loadQueue = useCallback(async (after?: string, append = false) => {
    const response = await fetch("/api/graphql", {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ operationName: "ApprovalQueue", variables: { first: PAGE_SIZE, after: after ?? null } }),
    });

    if (response.status === 401 || response.status === 403) {
      setState("unauthorized");
      return;
    }
    if (!response.ok) {
      throw new Error(`Could not load approvals (${response.status}).`);
    }

    const result = (await response.json()) as GraphQlResponse<ApprovalQueueQuery>;
    if (result.errors?.length || !result.data) {
      throw new Error(result.errors?.[0]?.message ?? "Could not load approvals.");
    }

    const page = result.data.approvalQueue;
    setItems((previous) => (append ? [...previous, ...page.items] : page.items));
    setNextCursor(page.nextCursor);
    setState(page.items.length === 0 && !append ? "empty" : "ready");
  }, []);

  useEffect(() => {
    let active = true;
    async function initialise() {
      try {
        const sessionResponse = await fetch("/api/session", { credentials: "same-origin" });
        if (!sessionResponse.ok) {
          if (active) setState("unauthorized");
          return;
        }
        const session = (await sessionResponse.json()) as SessionResponse;
        if (!session.claims.roles.includes("payment_approver")) {
          if (active) setState("unauthorized");
          return;
        }
        if (active) setCurrentUserId(session.claims.sub);
        await loadQueue();
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : "Could not load approvals.");
          setState("error");
        }
      }
    }
    void initialise();
    return () => {
      active = false;
    };
  }, [loadQueue]);

  async function decide(target: ApprovalItem, decision: "approve" | "reject", comment?: string) {
    setDecisionInFlight(true);
    try {
      const response = await fetch(`/api/payments/${encodeURIComponent(target.paymentId)}/${decision}`, {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json",
          "x-csrf-token": readCookie("sepa_csrf") ?? "",
          "Idempotency-Key": browserRandomUUID(),
        },
        body: JSON.stringify({ decisionComment: comment }),
      });
      if (!response.ok) {
        const problem = (await response.json().catch(() => null)) as ProblemDetail | null;
        const message = describeFailure(response, problem);
        toast.error(message);
        return;
      }

      // The queue is deliberately not changed locally. A command only becomes visible
      // after the authoritative server query has completed.
      await loadQueue();
      toast.success(decision === "approve" ? "Approval recorded." : "Rejection recorded.");
    } catch (decisionError) {
      toast.error(decisionError instanceof Error ? decisionError.message : "Approval decision failed.");
    } finally {
      setDecisionInFlight(false);
      setApproveTarget(undefined);
      setRejectTarget(undefined);
      setRejectComment("");
    }
  }

  return (
    <Card data-testid="payments.approvals.queue">
      <CardHeader>
        <CardTitle>Approval queue</CardTitle>
        <p className="text-sm text-muted-foreground">Payments awaiting an independent approval decision.</p>
      </CardHeader>
      <CardContent>
        {state === "loading" && <p role="status" data-testid="payments.approvals.queue.loading">Loading approvals…</p>}
        {state === "unauthorized" && <p role="alert" data-testid="payments.approvals.queue.unauthorized">You do not have access to the approval queue.</p>}
        {state === "error" && <p role="alert" data-testid="payments.approvals.queue.error">{error}</p>}
        {state === "empty" && <p role="status" data-testid="payments.approvals.queue.empty">No payments await approval.</p>}
        {state === "ready" && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-220 text-left text-sm" data-testid="payments.approvals.queue.table">
              <caption className="sr-only">Payments awaiting an approval decision</caption>
              <thead className="border-b text-muted-foreground">
                <tr>
                  <th className="p-2">Payment</th><th className="p-2">Amount</th><th className="p-2">Maker</th><th className="p-2">Submitted</th><th className="p-2">Status</th><th className="p-2">Actions</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => {
                  const selfApproval = item.makerUserId === currentUserId;
                  return <tr key={item.approvalId} className="border-b align-top" data-testid={`payments.approvals.queue.row.${item.paymentId}`}>
                    <td className="p-2"><a className="underline underline-offset-2" href={`/payments/${encodeURIComponent(item.paymentId)}`}>{item.paymentId}</a><div className="text-xs text-muted-foreground">Rule {item.matrixRuleId}</div></td>
                    <td className="p-2">{item.amount} {item.currency}<div className="text-xs text-muted-foreground">{item.debtorIban} → {item.creditorIban}</div></td>
                    <td className="p-2">{item.makerUserId}</td>
                    <td className="p-2">{new Date(item.submittedAt).toLocaleString()}<div className="text-xs text-muted-foreground">Expires {new Date(item.expiresAt).toLocaleString()}</div></td>
                    <td className="p-2"><Badge variant={item.expiredButUnprocessed ? "destructive" : "secondary"}>{approvalStatus(item)}</Badge></td>
                    <td className="p-2"><div className="flex flex-wrap gap-2">
                      <Button size="sm" data-testid={`payments.approvals.queue.approve.${item.paymentId}`} disabled={selfApproval || decisionInFlight} title={selfApproval ? "A maker cannot approve their own payment." : undefined} onClick={() => setApproveTarget(item)}>Approve</Button>
                      <Button size="sm" variant="outline" data-testid={`payments.approvals.queue.reject.${item.paymentId}`} disabled={selfApproval || decisionInFlight} title={selfApproval ? "A maker cannot reject their own payment." : undefined} onClick={() => setRejectTarget(item)}>Reject</Button>
                      {selfApproval && <span className="basis-full text-xs text-muted-foreground">Maker/checker separation prevents your decision.</span>}
                    </div></td>
                  </tr>;
                })}
              </tbody>
            </table>
            {nextCursor && <Button className="mt-4" variant="outline" data-testid="payments.approvals.queue.load-more" onClick={() => void loadQueue(nextCursor, true)}>Load more</Button>}
          </div>
        )}
      </CardContent>

      <AlertDialog open={Boolean(approveTarget)} onOpenChange={(open) => !decisionInFlight && !open && setApproveTarget(undefined)}>
        <AlertDialogContent data-testid="payments.approvals.approve.confirm-dialog"><AlertDialogHeader><AlertDialogTitle>Approve payment?</AlertDialogTitle><AlertDialogDescription>Approval releases this payment into its existing lifecycle. The server will confirm the result before this queue changes.</AlertDialogDescription></AlertDialogHeader><AlertDialogFooter><AlertDialogCancel disabled={decisionInFlight}>Cancel</AlertDialogCancel><AlertDialogAction data-testid="payments.approvals.approve.confirm-button" disabled={decisionInFlight} onClick={() => approveTarget && void decide(approveTarget, "approve")}>{decisionInFlight ? "Approving…" : "Approve payment"}</AlertDialogAction></AlertDialogFooter></AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={Boolean(rejectTarget)} onOpenChange={(open) => !decisionInFlight && !open && setRejectTarget(undefined)}>
        <AlertDialogContent data-testid="payments.approvals.reject.dialog"><AlertDialogHeader><AlertDialogTitle>Reject payment</AlertDialogTitle><AlertDialogDescription>A nonblank rejection comment is required by the command contract.</AlertDialogDescription></AlertDialogHeader><Field><FieldLabel htmlFor="approval-reject-comment">Rejection comment</FieldLabel><Input id="approval-reject-comment" data-testid="payments.approvals.reject.comment-input" value={rejectComment} onChange={(event) => setRejectComment(event.target.value)} aria-invalid={Boolean(rejectTarget && !rejectComment.trim())} /></Field>{rejectTarget && !rejectComment.trim() && <FieldError data-testid="payments.approvals.reject.comment-error">Enter a rejection comment.</FieldError>}<AlertDialogFooter><AlertDialogCancel disabled={decisionInFlight}>Cancel</AlertDialogCancel><AlertDialogAction data-testid="payments.approvals.reject.confirm-button" disabled={decisionInFlight || !rejectComment.trim()} onClick={() => rejectTarget && void decide(rejectTarget, "reject", rejectComment.trim())}>{decisionInFlight ? "Rejecting…" : "Reject payment"}</AlertDialogAction></AlertDialogFooter></AlertDialogContent>
      </AlertDialog>
    </Card>
  );
}
