import { NextRequest, NextResponse } from "next/server";
import { bffConfig, oidcConfig } from "@/lib/oidc-config";
import { deleteSession, getSession } from "@/lib/session-store";
import { CSRF_COOKIE, SESSION_COOKIE, isSecureRequest } from "@/lib/session-cookies";

export async function GET(request: NextRequest) {
  const sessionId = request.cookies.get(SESSION_COOKIE)?.value;
  const session = sessionId ? getSession(sessionId) : undefined;

  if (sessionId) {
    deleteSession(sessionId);
  }

  const endSessionUrl = new URL(oidcConfig.endSessionEndpoint);
  if (session) {
    endSessionUrl.searchParams.set("id_token_hint", session.idToken);
  }
  endSessionUrl.searchParams.set("post_logout_redirect_uri", bffConfig.postLogoutRedirectUri);

  const secure = isSecureRequest(request.url);
  const response = NextResponse.redirect(endSessionUrl);
  response.cookies.set(SESSION_COOKIE, "", { httpOnly: true, secure, sameSite: "lax", path: "/", maxAge: 0 });
  response.cookies.set(CSRF_COOKIE, "", { httpOnly: false, secure, sameSite: "lax", path: "/", maxAge: 0 });
  return response;
}
