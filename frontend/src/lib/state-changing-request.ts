import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { isValidCsrf } from "@/lib/csrf";
import { getSession, type SessionRecord } from "@/lib/session-store";
import { SESSION_COOKIE } from "@/lib/session-cookies";

type Authorized = { ok: true; session: SessionRecord };
type Unauthorized = { ok: false; response: NextResponse };

/**
 * Every state-changing BFF route validates session, CSRF, method, and content
 * type itself (per the nextjs-bff-route skill) — this is the shared check,
 * not a router-level bypass of that rule.
 */
export function authorizeStateChangingRequest(request: NextRequest): Authorized | Unauthorized {
  const sessionId = request.cookies.get(SESSION_COOKIE)?.value;
  const session = sessionId ? getSession(sessionId) : undefined;
  if (!session) {
    return {
      ok: false,
      response: NextResponse.json(
        { type: "about:blank", title: "Not authenticated", status: 401 },
        { status: 401 },
      ),
    };
  }

  if (!isValidCsrf(request, session)) {
    return {
      ok: false,
      response: NextResponse.json(
        { type: "about:blank", title: "Missing or invalid CSRF token", status: 403 },
        { status: 403 },
      ),
    };
  }

  const contentType = request.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) {
    return {
      ok: false,
      response: NextResponse.json(
        { type: "about:blank", title: "Unsupported Content-Type", status: 415 },
        { status: 415 },
      ),
    };
  }

  return { ok: true, session };
}
