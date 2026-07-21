import { randomUUID } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { backendConfig } from "@/lib/oidc-config";
import { getSession } from "@/lib/session-store";
import { SESSION_COOKIE } from "@/lib/session-cookies";

const CORRELATION_ID_HEADER = "X-Correlation-Id";
const MAX_BODY_BYTES = 16 * 1024;

const OPERATIONS = {
  ApprovalQueue: `query ApprovalQueue($first: Int!, $after: String) {
    approvalQueue(first: $first, after: $after) {
      items { approvalId paymentId approvalStatus makerUserId submittedAt expiresAt expiredButUnprocessed matrixRuleId amount currency debtorIban creditorIban }
      nextCursor
    }
  }`,
  Approval: `query Approval($paymentId: ID!) {
    approval(paymentId: $paymentId) {
      approvalId paymentId approvalStatus makerUserId submittedAt expiresAt expiredButUnprocessed matrixRuleId amount currency debtorIban creditorIban decisionComment decidedAt
    }
  }`,
  PaymentAuditTrail: `query PaymentAuditTrail($paymentId: ID!, $first: Int!, $after: String) { paymentAuditTrail(paymentId: $paymentId, first: $first, after: $after) { items { auditEntryId tenantId branchId occurredAt actorType actorId authorizedRole correlationId commandType targetType targetId paymentId batchId outcome decisionComment beforeState { approvalId approvalStatus } afterState { approvalId approvalStatus } } nextCursor } }`,
  PaymentIsoEvidence: `query PaymentIsoEvidence($paymentId: ID!) { paymentIsoEvidence(paymentId: $paymentId) { paymentId messages { isoMessageId messageType messageVersion versionEffectiveFrom lineageRole lineageRecordedAt } identifiers { isoMessageId type value } } }`,
  AuditEntries: `query AuditEntries($auditFilter: AuditQueryFilter!, $first: Int!, $after: String) { auditEntries(filter: $auditFilter, first: $first, after: $after) { items { auditEntryId tenantId branchId occurredAt actorType actorId authorizedRole correlationId commandType targetType targetId paymentId batchId outcome decisionComment beforeState { approvalId approvalStatus } afterState { approvalId approvalStatus } } nextCursor } }`,
} as const;

type OperationName = keyof typeof OPERATIONS;

function isOperationName(value: unknown): value is OperationName {
  return typeof value === "string" && value in OPERATIONS;
}

export async function POST(request: NextRequest) {
  const sessionId = request.cookies.get(SESSION_COOKIE)?.value;
  const session = sessionId ? getSession(sessionId) : undefined;
  if (!session) return NextResponse.json({ type: "about:blank", title: "Not authenticated", status: 401 }, { status: 401 });

  const contentLength = Number(request.headers.get("content-length") ?? "0");
  if (!Number.isFinite(contentLength) || contentLength > MAX_BODY_BYTES) {
    return NextResponse.json({ type: "about:blank", title: "Request too large", status: 413 }, { status: 413 });
  }
  let body: { operationName?: unknown; variables?: unknown };
  let rawBody: string;
  try {
    rawBody = await request.text();
    if (new TextEncoder().encode(rawBody).byteLength > MAX_BODY_BYTES) {
      return NextResponse.json({ type: "about:blank", title: "Request too large", status: 413 }, { status: 413 });
    }
    body = JSON.parse(rawBody) as { operationName?: unknown; variables?: unknown };
  } catch {
    return NextResponse.json({ type: "about:blank", title: "Malformed JSON body", status: 400 }, { status: 400 });
  }
  if (!isOperationName(body.operationName) || (body.variables !== undefined && (typeof body.variables !== "object" || body.variables === null || Array.isArray(body.variables)))) {
    return NextResponse.json({ type: "about:blank", title: "Unsupported GraphQL operation", status: 400 }, { status: 400 });
  }

  const correlationId = request.headers.get(CORRELATION_ID_HEADER) ?? randomUUID();
  const backendResponse = await fetch(`${backendConfig.baseUrl}/graphql`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${session.accessToken}`, [CORRELATION_ID_HEADER]: correlationId },
    body: JSON.stringify({ operationName: body.operationName, query: OPERATIONS[body.operationName], variables: body.variables ?? {} }),
    cache: "no-store",
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody.length > 0 ? responseBody : null, {
    status: backendResponse.status,
    headers: { "Content-Type": backendResponse.headers.get("content-type") ?? "application/json", [CORRELATION_ID_HEADER]: correlationId },
  });
}
