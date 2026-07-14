// In-memory store for in-flight OIDC transactions (between /api/auth/login and
// /api/auth/callback). An in-memory Map is sufficient for Iteration 0 — a
// horizontally-scaled BFF would need Redis or another shared store instead.

const TXN_TTL_MS = 5 * 60 * 1000;

interface PendingAuthTxn {
  state: string;
  codeVerifier: string;
  nonce: string;
  createdAt: number;
}

// See session-store.ts for why this is anchored on globalThis rather than a
// bare module-scope Map.
const globalForPendingTxns = globalThis as unknown as { __sepaPendingTxns?: Map<string, PendingAuthTxn> };
const pendingTxns = globalForPendingTxns.__sepaPendingTxns ?? new Map<string, PendingAuthTxn>();
globalForPendingTxns.__sepaPendingTxns = pendingTxns;

function purgeExpired(): void {
  const now = Date.now();
  for (const [state, txn] of pendingTxns) {
    if (now - txn.createdAt > TXN_TTL_MS) {
      pendingTxns.delete(state);
    }
  }
}

export function createPendingAuthTxn(state: string, codeVerifier: string, nonce: string): void {
  purgeExpired();
  pendingTxns.set(state, { state, codeVerifier, nonce, createdAt: Date.now() });
}

/** Consumes (removes) the pending transaction for `state`, if present and not expired. */
export function takePendingAuthTxn(state: string): PendingAuthTxn | undefined {
  const txn = pendingTxns.get(state);
  pendingTxns.delete(state);
  if (!txn) {
    return undefined;
  }
  if (Date.now() - txn.createdAt > TXN_TTL_MS) {
    return undefined;
  }
  return txn;
}
