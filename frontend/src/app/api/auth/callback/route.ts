import { jwtVerify, createRemoteJWKSet, type JWTVerifyGetKey } from "jose";
import { NextRequest, NextResponse } from "next/server";
import { bffConfig, oidcConfig } from "@/lib/oidc-config";
import { takePendingAuthTxn } from "@/lib/pending-auth-store";
import { randomToken } from "@/lib/pkce";
import { createSession, type SessionClaims, type SessionRecord } from "@/lib/session-store";
import { CSRF_COOKIE, SESSION_COOKIE, isSecureRequest } from "@/lib/session-cookies";

interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  id_token: string;
  expires_in: number;
  token_type: string;
}

// Built lazily (on first request), not at module import time — see the
// oidc-config.ts comment on why eager env reads at module scope break builds.
let jwksCache: JWTVerifyGetKey | undefined;
function getJwks(): JWTVerifyGetKey {
  if (!jwksCache) {
    jwksCache = createRemoteJWKSet(new URL(oidcConfig.jwksUri));
  }
  return jwksCache;
}

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

// Roles come from the access token, not the ID token: this realm's "realm roles" client
// scope mapper is only configured to add `realm_access.roles` to the access token (verified
// empirically — the ID token carries tenant_id/branch_id/preferred_username but no
// realm_access at all). The BFF already holds the access token as a trusted, direct
// token-endpoint response (never client-supplied), so decoding it for a UI-nav hint is safe;
// the backend independently re-verifies the same JWT and its roles via @PreAuthorize on every
// forwarded request, so this is not itself a security boundary.
function extractClaims(idTokenPayload: Record<string, unknown>, accessTokenPayload: Record<string, unknown>): SessionClaims {
  const roles = extractRoles(accessTokenPayload);

  return {
    sub: String(idTokenPayload.sub),
    preferredUsername:
      typeof idTokenPayload.preferred_username === "string"
        ? idTokenPayload.preferred_username
        : null,
    // The realm's frozen Organization mapper places the stable tenant UUID under
    // `organization.<alias>.tenant_id`. The backend performs the same one-org
    // normalization before installing RLS GUCs; the BFF needs it only for its
    // trusted server-session display metadata.
    tenantId: typeof idTokenPayload.tenant_id === "string"
      ? idTokenPayload.tenant_id
      : organizationTenantId(idTokenPayload),
    branchId: typeof idTokenPayload.branch_id === "string" ? idTokenPayload.branch_id : null,
    roles,
  };
}

export async function GET(request: NextRequest) {
  const url = new URL(request.url);
  const code = url.searchParams.get("code");
  const state = url.searchParams.get("state");
  const error = url.searchParams.get("error");

  if (error) {
    return NextResponse.json({ type: "about:blank", title: "OIDC error", detail: error }, { status: 400 });
  }
  if (!code || !state) {
    return NextResponse.json(
      { type: "about:blank", title: "Missing code or state", status: 400 },
      { status: 400 },
    );
  }

  const txn = takePendingAuthTxn(state);
  if (!txn) {
    return NextResponse.json(
      { type: "about:blank", title: "Unknown or expired auth state", status: 400 },
      { status: 400 },
    );
  }

  const tokenResponse = await fetch(oidcConfig.tokenEndpoint, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      code,
      redirect_uri: bffConfig.redirectUri,
      client_id: oidcConfig.clientId,
      client_secret: oidcConfig.clientSecret,
      code_verifier: txn.codeVerifier,
    }),
  });

  if (!tokenResponse.ok) {
    return NextResponse.json(
      { type: "about:blank", title: "Token exchange failed", status: 502 },
      { status: 502 },
    );
  }

  const tokens = (await tokenResponse.json()) as TokenResponse;

  const { payload } = await jwtVerify(tokens.id_token, getJwks(), {
    issuer: oidcConfig.issuer,
    audience: oidcConfig.clientId,
  });

  if (payload.nonce !== txn.nonce) {
    return NextResponse.json(
      { type: "about:blank", title: "Nonce mismatch", status: 400 },
      { status: 400 },
    );
  }

  // The access token has no `aud` claim in this realm (only `azp`) — verify issuer/signature
  // only, matching how the resource server (backend SecurityConfig) validates it.
  const { payload: accessTokenPayload } = await jwtVerify(tokens.access_token, getJwks(), {
    issuer: oidcConfig.issuer,
  });

  const sessionId = randomToken();
  const csrfToken = randomToken();
  const record: SessionRecord = {
    sessionId,
    csrfToken,
    accessToken: tokens.access_token,
    refreshToken: tokens.refresh_token ?? null,
    idToken: tokens.id_token,
    claims: extractClaims(payload, accessTokenPayload),
    accessTokenExpiresAt: Date.now() + tokens.expires_in * 1000,
    createdAt: Date.now(),
  };
  createSession(record);

  const secure = isSecureRequest(request.url);
  const response = NextResponse.redirect(new URL("/", request.url));
  response.cookies.set(SESSION_COOKIE, sessionId, {
    httpOnly: true,
    secure,
    sameSite: "lax",
    path: "/",
    maxAge: Math.floor(tokens.expires_in),
  });
  response.cookies.set(CSRF_COOKIE, csrfToken, {
    httpOnly: false,
    secure,
    sameSite: "lax",
    path: "/",
    maxAge: Math.floor(tokens.expires_in),
  });
  return response;
}
