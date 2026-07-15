import { randomUUID } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { backendConfig } from "@/lib/oidc-config";
import { getSession } from "@/lib/session-store";
import { SESSION_COOKIE } from "@/lib/session-cookies";

const CORRELATION_ID_HEADER = "X-Correlation-Id";

export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const sessionId = request.cookies.get(SESSION_COOKIE)?.value;
  const session = sessionId ? getSession(sessionId) : undefined;
  if (!session) {
    return NextResponse.json(
      { type: "about:blank", title: "Not authenticated", status: 401 },
      { status: 401 },
    );
  }

  const { id } = await params;
  const correlationId = request.headers.get(CORRELATION_ID_HEADER) ?? randomUUID();
  const query = request.nextUrl.searchParams.toString();
  const backendUrl = `${backendConfig.baseUrl}/api/v1/payments/${id}/timeline${query ? `?${query}` : ""}`;
  const backendResponse = await fetch(backendUrl, {
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
