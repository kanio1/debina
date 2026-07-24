import { isValidCsrf } from "./csrf.ts";
import {
  isXmlContentType,
  rejectsUntrustedBackendDestination,
} from "./pain001-upload.ts";
import type { SessionRecord } from "./session-store.ts";
import { SESSION_COOKIE } from "./session-cookies.ts";
import { getSession } from "./session-store.ts";

export interface XmlUploadAuthRequest {
  nextUrl: URL;
  cookies: {
    get(name: string): { value: string } | undefined;
  };
  headers: {
    get(name: string): string | null;
  };
}

type Authorized = { ok: true; session: SessionRecord };
type Unauthorized = { ok: false; status: number; title: string };

/** Shared session, CSRF and content-type gate for the pain.001 upload adapter. */
export function authorizeXmlUploadRequestCore(request: XmlUploadAuthRequest): Authorized | Unauthorized {
  if (rejectsUntrustedBackendDestination(request)) {
    return {
      ok: false,
      status: 400,
      title: "Untrusted backend destination rejected",
    };
  }

  const sessionId = request.cookies.get(SESSION_COOKIE)?.value;
  const session = sessionId ? getSession(sessionId) : undefined;
  if (!session) {
    return {
      ok: false,
      status: 401,
      title: "Not authenticated",
    };
  }

  if (!isValidCsrf(request as Parameters<typeof isValidCsrf>[0], session)) {
    return {
      ok: false,
      status: 403,
      title: "Missing or invalid CSRF token",
    };
  }

  const contentType = request.headers.get("content-type") ?? "";
  if (!isXmlContentType(contentType)) {
    return {
      ok: false,
      status: 415,
      title: "Unsupported Content-Type",
    };
  }

  return { ok: true, session };
}
