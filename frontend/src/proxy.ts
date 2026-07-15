import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { applySecurityHeaders } from "@/lib/security-headers";

// Renamed from `middleware.ts` per Next.js 16.2 — see
// node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/proxy.md.
// Security headers only: session/CSRF/method/content-type validation is owned by
// each state-changing route handler (per the nextjs-bff-route skill).
export function proxy(request: NextRequest) {
  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  applySecurityHeaders(response.headers, nonce);
  return response;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
