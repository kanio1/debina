// In-memory server-side session store, keyed by an opaque session id that is
// the only thing the browser ever holds (as an HttpOnly cookie). Iteration 0
// scope: an in-memory Map is fine for a single BFF instance; a later
// iteration upgrades this to Redis for horizontal scaling.

export interface SessionClaims {
  sub: string;
  preferredUsername: string | null;
  tenantId: string | null;
  branchId: string | null;
  roles: string[];
}

export interface SessionRecord {
  sessionId: string;
  csrfToken: string;
  accessToken: string;
  refreshToken: string | null;
  idToken: string;
  claims: SessionClaims;
  accessTokenExpiresAt: number;
  createdAt: number;
}

// Next.js bundles Route Handlers and Server Components as separate server
// entry points, each with its own copy of a plain module-scope variable — a
// bare `new Map()` here is NOT actually shared between e.g. /api/auth/callback
// and a page's Server Component, even within a single `next start` process
// (verified empirically: a session created via the callback route was
// invisible to a Server Component reading the same module). Anchoring the
// Map on `globalThis` is the standard workaround and keeps this genuinely a
// single in-memory store, matching the Iteration 0 intent.
const globalForSessions = globalThis as unknown as { __sepaSessions?: Map<string, SessionRecord> };
const sessions = globalForSessions.__sepaSessions ?? new Map<string, SessionRecord>();
globalForSessions.__sepaSessions = sessions;

export function createSession(record: SessionRecord): void {
  sessions.set(record.sessionId, record);
}

export function getSession(sessionId: string): SessionRecord | undefined {
  const record = sessions.get(sessionId);
  if (!record) {
    return undefined;
  }
  if (Date.now() >= record.accessTokenExpiresAt) {
    sessions.delete(sessionId);
    return undefined;
  }
  return record;
}

export function deleteSession(sessionId: string): void {
  sessions.delete(sessionId);
}
