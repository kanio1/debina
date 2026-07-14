import { randomUUID } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { backendConfig } from "@/lib/oidc-config";
import { authorizeStateChangingRequest } from "@/lib/state-changing-request";
import { getSession } from "@/lib/session-store";
import { SESSION_COOKIE } from "@/lib/session-cookies";

const CORRELATION_ID_HEADER = "X-Correlation-Id";
const IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

export async function GET(request: NextRequest) {
  const sessionId = request.cookies.get(SESSION_COOKIE)?.value;
  const session = sessionId ? getSession(sessionId) : undefined;
  if (!session) {
    return NextResponse.json(
      { type: "about:blank", title: "Not authenticated", status: 401 },
      { status: 401 },
    );
  }

  const correlationId = request.headers.get(CORRELATION_ID_HEADER) ?? randomUUID();
  const backendResponse = await fetch(`${backendConfig.baseUrl}/api/v1/payments`, {
    headers: {
      Authorization: `Bearer ${session.accessToken}`,
      [CORRELATION_ID_HEADER]: correlationId,
    },
    cache: "no-store",
  });

  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody.length > 0 ? responseBody : null, {
    status: backendResponse.status,
    headers: {
      "Content-Type": backendResponse.headers.get("content-type") ?? "application/json",
      [CORRELATION_ID_HEADER]: correlationId,
    },
  });
}

export async function POST(request: NextRequest) {
  const authz = authorizeStateChangingRequest(request);
  if (!authz.ok) {
    return authz.response;
  }

  const idempotencyKey = request.headers.get(IDEMPOTENCY_KEY_HEADER);
  if (!idempotencyKey) {
    return NextResponse.json(
      { type: "about:blank", title: "Missing Idempotency-Key header", status: 400 },
      { status: 400 },
    );
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(
      { type: "about:blank", title: "Malformed JSON body", status: 400 },
      { status: 400 },
    );
  }

  const correlationId = request.headers.get(CORRELATION_ID_HEADER) ?? randomUUID();

  const backendResponse = await fetch(`${backendConfig.baseUrl}/api/v1/payments`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${authz.session.accessToken}`,
      [CORRELATION_ID_HEADER]: correlationId,
      [IDEMPOTENCY_KEY_HEADER]: idempotencyKey,
    },
    body: JSON.stringify(body),
  });

  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody.length > 0 ? responseBody : null, {
    status: backendResponse.status,
    headers: {
      "Content-Type": backendResponse.headers.get("content-type") ?? "application/json",
      [CORRELATION_ID_HEADER]: correlationId,
    },
  });
}
