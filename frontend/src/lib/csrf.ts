import { timingSafeEqual } from "node:crypto";
import type { NextRequest } from "next/server";
import type { SessionRecord } from "@/lib/session-store";

export const CSRF_HEADER = "x-csrf-token";

/** Double-submit check: the header value must match the token bound to this session. */
export function isValidCsrf(request: NextRequest, session: SessionRecord): boolean {
  const headerToken = request.headers.get(CSRF_HEADER);
  if (!headerToken) {
    return false;
  }
  const expected = Buffer.from(session.csrfToken);
  const actual = Buffer.from(headerToken);
  if (expected.length !== actual.length) {
    return false;
  }
  return timingSafeEqual(expected, actual);
}
