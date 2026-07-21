"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ScreenState } from "@/components/shared/screen-state";
import type { AuditEntriesQuery, AuditQueryFilter } from "@/generated/graphql";

type State = "loading" | "ready" | "empty" | "unauthorized" | "error";
type Entry = AuditEntriesQuery["auditEntries"]["items"][number];
const PAGE_SIZE = 25;

function filtersFromSearch(search: URLSearchParams): AuditQueryFilter {
  return {
    tenantId: search.get("tenant") || null,
    branchId: null,
    targetType: search.get("targetType") || null,
    targetId: search.get("targetId") || null,
    paymentId: search.get("paymentId") || null,
    batchId: null,
    actorId: search.get("actor") || null,
    commandType: search.get("command") || null,
    outcome: search.get("outcome") || null,
    correlationId: search.get("correlationId") || null,
    occurredFrom: null,
    occurredTo: null,
  };
}

export default function EvidencePage() {
  const [state, setState] = useState<State>("loading");
  const [items, setItems] = useState<Entry[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams();
  const filter = useMemo(() => filtersFromSearch(search), [search]);

  const load = useCallback(async (after: string | null, append: boolean) => {
    const response = await fetch("/api/graphql", {
      method: "POST", credentials: "same-origin", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ operationName: "AuditEntries", variables: { auditFilter: filter, first: PAGE_SIZE, after } }),
    });
    if (response.status === 401 || response.status === 403) return setState("unauthorized");
    if (!response.ok) throw new Error("Could not load audit records.");
    const body = await response.json() as { data?: AuditEntriesQuery; errors?: unknown[] };
    const page = body.data?.auditEntries;
    if (body.errors?.length || !page) throw new Error("Could not load audit records.");
    setItems(previous => append ? [...previous, ...page.items] : page.items);
    setNextCursor(page.nextCursor);
    setState(page.items.length || append ? "ready" : "empty");
  }, [filter]);

  useEffect(() => { queueMicrotask(() => { void load(null, false).catch(() => setState("error")); }); }, [load]);

  function updateFilter(name: string, value: string) {
    const next = new URLSearchParams(search);
    if (value) next.set(name, value); else next.delete(name);
    router.replace(`${pathname}?${next.toString()}`);
  }

  return (
    <div className="flex flex-col gap-4" data-testid="evidence.workspace.page">
      <h1 className="text-lg font-semibold">Evidence / Audit</h1>
      <div className="grid gap-2 sm:grid-cols-2">
        <Input aria-label="Tenant" data-testid="evidence.filter.tenant" defaultValue={filter.tenantId ?? ""} onChange={e => updateFilter("tenant", e.target.value)} placeholder="Tenant ID" />
        <Input aria-label="Payment ID" data-testid="evidence.filter.payment-id" defaultValue={filter.paymentId ?? ""} onChange={e => updateFilter("paymentId", e.target.value)} placeholder="Payment ID" />
        <Input aria-label="Actor" data-testid="evidence.filter.actor" defaultValue={filter.actorId ?? ""} onChange={e => updateFilter("actor", e.target.value)} placeholder="Actor" />
        <Input aria-label="Command type" data-testid="evidence.filter.command" defaultValue={filter.commandType ?? ""} onChange={e => updateFilter("command", e.target.value)} placeholder="Command type" />
        <Input aria-label="Target type" data-testid="evidence.filter.target-type" defaultValue={filter.targetType ?? ""} onChange={e => updateFilter("targetType", e.target.value)} placeholder="Target type" />
        <Input aria-label="Target ID" data-testid="evidence.filter.target-id" defaultValue={filter.targetId ?? ""} onChange={e => updateFilter("targetId", e.target.value)} placeholder="Target ID" />
        <Input aria-label="Outcome" data-testid="evidence.filter.outcome" defaultValue={filter.outcome ?? ""} onChange={e => updateFilter("outcome", e.target.value)} placeholder="Outcome" />
        <Input aria-label="Correlation ID" data-testid="evidence.filter.correlation-id" defaultValue={filter.correlationId ?? ""} onChange={e => updateFilter("correlationId", e.target.value)} placeholder="Correlation ID" />
      </div>
      {state !== "ready" && <ScreenState kind={state} testIdPrefix="evidence.workspace" message={state === "empty" ? "No audit records match." : undefined} />}
      {state === "ready" && <><div className="overflow-auto"><table data-testid="evidence.audit.table" className="w-full text-left text-sm"><caption className="sr-only">Cross-tenant audit records</caption><thead><tr><th>Tenant</th><th>When</th><th>Actor</th><th>Command</th><th>Outcome</th></tr></thead><tbody>{items.map(e => <tr key={e.auditEntryId} data-testid="evidence.audit.row"><td>{e.tenantId}</td><td>{new Date(e.occurredAt).toLocaleString()}</td><td>{e.actorId}</td><td>{e.commandType}</td><td>{e.outcome}</td></tr>)}</tbody></table></div>{nextCursor && <Button variant="outline" data-testid="evidence.audit.load-more" onClick={() => void load(nextCursor, true).catch(() => setState("error"))}>Load more</Button>}</>}
    </div>
  );
}
