import { randomUUID } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { backendConfig } from "@/lib/oidc-config";
import { authorizeStateChangingRequest } from "@/lib/state-changing-request";

const CORRELATION_ID_HEADER = "X-Correlation-Id";

export async function forwardApprovalDecision(request: NextRequest, paymentId: string, decision: "approve" | "reject") {
  const authz = authorizeStateChangingRequest(request);
  if (!authz.ok) return authz.response;
  let body: { decisionComment?: unknown };
  try { body = await request.json(); } catch { return NextResponse.json({ type: "about:blank", title: "Malformed JSON body", status: 400 }, { status: 400 }); }
  const comment = typeof body.decisionComment === "string" ? body.decisionComment.trim() : "";
  if (decision === "reject" && !comment) return NextResponse.json({ type: "about:blank", title: "Rejection comment is required", status: 422 }, { status: 422 });
  const correlationId = request.headers.get(CORRELATION_ID_HEADER) ?? randomUUID();
  const backendResponse = await fetch(`${backendConfig.baseUrl}/api/v1/payments/${encodeURIComponent(paymentId)}/${decision}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${authz.session.accessToken}`, "Idempotency-Key": request.headers.get("Idempotency-Key") ?? randomUUID(), [CORRELATION_ID_HEADER]: correlationId },
    body: JSON.stringify(comment ? { decisionComment: comment } : {}),
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody.length > 0 ? responseBody : null, { status: backendResponse.status, headers: { "Content-Type": backendResponse.headers.get("content-type") ?? "application/json", [CORRELATION_ID_HEADER]: correlationId } });
}
