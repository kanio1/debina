import type { SessionClaims } from "@/lib/session-store";

function extractRoles(tokenPayload: Record<string, unknown>): string[] {
  const realmAccess = tokenPayload["realm_access"];
  return realmAccess &&
    typeof realmAccess === "object" &&
    Array.isArray((realmAccess as { roles?: unknown }).roles)
    ? (realmAccess as { roles: unknown[] }).roles.filter(
        (role): role is string => typeof role === "string",
      )
    : [];
}

function organizationTenantId(tokenPayload: Record<string, unknown>): string | null {
  const organization = tokenPayload.organization;
  if (!organization || typeof organization !== "object" || Array.isArray(organization)) return null;
  const entries = Object.values(organization as Record<string, unknown>);
  if (entries.length !== 1 || !entries[0] || typeof entries[0] !== "object" || Array.isArray(entries[0])) return null;
  const tenantIds = (entries[0] as Record<string, unknown>).tenant_id;
  if (!Array.isArray(tenantIds)) return null;
  const tenantId = tenantIds.find((value): value is string => typeof value === "string" && value.length > 0);
  return tenantId ?? null;
}

/**
 * Projects already verified OIDC token claims into the browser-safe server
 * session. `preferred_username` is optional under the current sepa-web scopes;
 * `sub` and roles remain the stable D3A identity and authorization evidence.
 */
export function projectSessionClaims(
  idTokenPayload: Record<string, unknown>,
  accessTokenPayload: Record<string, unknown>,
): SessionClaims {
  return {
    sub: String(idTokenPayload.sub),
    preferredUsername:
      typeof idTokenPayload.preferred_username === "string"
        ? idTokenPayload.preferred_username
        : null,
    tenantId: typeof idTokenPayload.tenant_id === "string"
      ? idTokenPayload.tenant_id
      : organizationTenantId(idTokenPayload),
    branchId: typeof idTokenPayload.branch_id === "string" ? idTokenPayload.branch_id : null,
    roles: extractRoles(accessTokenPayload),
  };
}
