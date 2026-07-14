import { NextRequest, NextResponse } from "next/server";
import { bffConfig, oidcConfig } from "@/lib/oidc-config";
import { deriveCodeChallenge, generateCodeVerifier, randomToken } from "@/lib/pkce";
import { createPendingAuthTxn } from "@/lib/pending-auth-store";

export async function GET(_request: NextRequest) {
  const state = randomToken();
  const nonce = randomToken();
  const codeVerifier = generateCodeVerifier();
  const codeChallenge = deriveCodeChallenge(codeVerifier);

  createPendingAuthTxn(state, codeVerifier, nonce);

  const authorizeUrl = new URL(oidcConfig.authorizationEndpoint);
  authorizeUrl.searchParams.set("client_id", oidcConfig.clientId);
  authorizeUrl.searchParams.set("response_type", "code");
  authorizeUrl.searchParams.set("scope", "openid profile");
  authorizeUrl.searchParams.set("redirect_uri", bffConfig.redirectUri);
  authorizeUrl.searchParams.set("state", state);
  authorizeUrl.searchParams.set("nonce", nonce);
  authorizeUrl.searchParams.set("code_challenge", codeChallenge);
  authorizeUrl.searchParams.set("code_challenge_method", "S256");

  return NextResponse.redirect(authorizeUrl);
}
