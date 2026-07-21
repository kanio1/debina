"use client";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { ScreenState } from "@/components/shared/screen-state";
import type { PaymentAuditTrailQuery } from "@/generated/graphql";
type State = "idle" | "loading" | "ready" | "empty" | "unauthorized" | "error";
type Entry = PaymentAuditTrailQuery["paymentAuditTrail"]["items"][number];

async function copyCorrelationId(correlationId: string, announce: (message: string) => void) {
 if (!navigator.clipboard?.writeText) {
  announce("Could not copy correlation ID.");
  return;
 }
 try {
  await navigator.clipboard.writeText(correlationId);
  announce("Correlation ID copied.");
 } catch {
  announce("Could not copy correlation ID.");
 }
}

export function AuditDrawer({ paymentId }: { paymentId: string }) {
 const [open,setOpen]=useState(false); const [state,setState]=useState<State>("idle"); const [items,setItems]=useState<Entry[]>([]); const [copyMessage,setCopyMessage]=useState("");
 useEffect(()=>{ if(!open)return; let active=true; queueMicrotask(()=>active&&setState("loading")); void fetch("/api/graphql",{method:"POST",credentials:"same-origin",headers:{"Content-Type":"application/json"},body:JSON.stringify({operationName:"PaymentAuditTrail",variables:{paymentId,first:25,after:null}})}).then(async r=>{if(!active)return;if(r.status===401||r.status===403)return setState("unauthorized");if(!r.ok)return setState("error");const b=await r.json() as {data?:PaymentAuditTrailQuery;errors?:unknown[]};const rows=b.data?.paymentAuditTrail.items;if(b.errors?.length||!rows)return setState("error");setItems(rows);setState(rows.length?"ready":"empty")}).catch(()=>active&&setState("error"));return()=>{active=false}},[open,paymentId]);
 return <Sheet open={open} onOpenChange={setOpen}><SheetTrigger render={<Button variant="outline" data-testid="payment.detail.evidence.trigger" />}>Evidence</SheetTrigger><SheetContent data-testid="evidence.record.drawer"><SheetHeader><SheetTitle>Evidence / Audit</SheetTitle></SheetHeader><p aria-live="polite" data-testid="audit.correlation-id.copy-status" className="sr-only">{copyMessage}</p>{state==="loading"&&<ScreenState kind="loading" testIdPrefix="evidence.record" />}{state==="empty"&&<ScreenState kind="empty" testIdPrefix="evidence.record" message="No audit entries." />}{state==="unauthorized"&&<ScreenState kind="unauthorized" testIdPrefix="evidence.record" />}{state==="error"&&<ScreenState kind="error" testIdPrefix="evidence.record" message="Audit history is unavailable." />}{state==="ready"&&<div className="overflow-auto"><table data-testid="audit.trail.table" className="w-full text-left text-sm"><thead><tr><th>When</th><th>Actor</th><th>Action</th><th>Outcome</th></tr></thead><tbody>{items.map(e=><tr key={e.auditEntryId}><td>{new Date(e.occurredAt).toLocaleString()}</td><td>{e.actorId} · {e.authorizedRole}</td><td>{e.commandType}</td><td>{e.outcome}</td></tr>)}</tbody></table><table data-testid="audit.command-history.table" className="mt-4 w-full text-left text-sm"><thead><tr><th>Command</th><th>Decision</th><th>Correlation</th></tr></thead><tbody>{items.map(e=><tr key={e.auditEntryId}><td>{e.commandType}</td><td><details><summary>View decision state</summary>{e.beforeState.approvalStatus??"—"} → {e.afterState.approvalStatus??"—"}</details></td><td>{e.correlationId ? <><span className="select-text">{e.correlationId}</span><Button size="xs" variant="ghost" data-testid="audit.correlation-id.copy-button" aria-label="Copy correlation ID" onClick={()=>void copyCorrelationId(e.correlationId,setCopyMessage)}>Copy</Button></> : "—"}</td></tr>)}</tbody></table></div>}</SheetContent></Sheet>;
}
