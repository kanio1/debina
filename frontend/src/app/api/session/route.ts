import { NextRequest, NextResponse } from "next/server";
import { getSession } from "@/lib/session-store";
import { SESSION_COOKIE } from "@/lib/session-cookies";

export async function GET(request: NextRequest) {
  const sessionId = request.cookies.get(SESSION_COOKIE)?.value;
  const session = sessionId ? getSession(sessionId) : undefined;

  if (!session) {
    return NextResponse.json(
      { type: "about:blank", title: "Not authenticated", status: 401 },
      { status: 401 },
    );
  }

  return NextResponse.json({ claims: session.claims });
}
