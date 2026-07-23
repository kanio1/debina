import { jwtVerify, createRemoteJWKSet, type JWTVerifyGetKey } from "jose";
import { NextRequest, NextResponse } from "next/server";
import { bffConfig, oidcConfig } from "@/lib/oidc-config";
import { takePendingAuthTxn } from "@/lib/pending-auth-store";
import { randomToken } from "@/lib/pkce";
import { createSession, type SessionRecord } from "@/lib/session-store";
import { CSRF_COOKIE, SESSION_COOKIE, isSecureRequest } from "@/lib/session-cookies";
import { postLoginRedirectUrl } from "@/lib/post-login-redirect";
import { projectSessionClaims } from "@/lib/session-claims";

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
    claims: projectSessionClaims(payload, accessTokenPayload),
    accessTokenExpiresAt: Date.now() + tokens.expires_in * 1000,
    createdAt: Date.now(),
  };
  createSession(record);

  const secure = isSecureRequest(request.url);
  const response = NextResponse.redirect(postLoginRedirectUrl(bffConfig.baseUrl));
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
