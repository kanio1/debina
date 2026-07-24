import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { authorizeXmlUploadRequestCore } from "./pain001-upload-auth.ts";
import type { SessionRecord } from "./session-store.ts";

type Authorized = { ok: true; session: SessionRecord };
type Unauthorized = { ok: false; response: NextResponse };

/** Shared session, CSRF and content-type gate for the pain.001 upload adapter. */
export function authorizeXmlUploadRequest(request: NextRequest): Authorized | Unauthorized {
  const authz = authorizeXmlUploadRequestCore(request);
  if (!authz.ok) {
    return {
      ok: false,
      response: NextResponse.json(
        { type: "about:blank", title: authz.title, status: authz.status },
        { status: authz.status },
      ),
    };
  }
  return { ok: true, session: authz.session };
}
