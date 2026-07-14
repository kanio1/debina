import { cookies } from "next/headers";
import { getSession, type SessionRecord } from "@/lib/session-store";
import { SESSION_COOKIE } from "@/lib/session-cookies";

/** Server Component helper — resolves the current request's session, if any. */
export async function getCurrentSession(): Promise<SessionRecord | undefined> {
  const store = await cookies();
  const sessionId = store.get(SESSION_COOKIE)?.value;
  return sessionId ? getSession(sessionId) : undefined;
}
