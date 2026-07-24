import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { NextRequest } from "next/server.js";
import {
  buildPain001BackendUrl,
  buffersEqual,
  forwardPain001Upload,
  frontendPaymentDetailPath,
  IDEMPOTENCY_KEY_HEADER,
  paymentIdFromBackendLocation,
  PAIN001_BACKEND_PATH,
  SIGNATURE_HEADER,
  SIGNER_ID_HEADER,
} from "../src/lib/pain001-upload.ts";
import { handlePain001UploadPost } from "../src/lib/pain001-upload-route-handler.ts";
import { authorizeXmlUploadRequestCore } from "../src/lib/pain001-upload-auth.ts";
import { CSRF_HEADER } from "../src/lib/csrf.ts";
import { createSession, type SessionRecord } from "../src/lib/session-store.ts";
import { SESSION_COOKIE } from "../src/lib/session-cookies.ts";

const backendBaseUrl = "http://backend:8081";
const paymentId = "11111111-2222-3333-4444-555555555555";
const sessionId = "session-e1-route-test";
const csrfToken = "csrf-token-e1-route-test";
const accessToken = "server-session-access-token";

function makeSessionRecord(): SessionRecord {
  return {
    sessionId,
    csrfToken,
    accessToken,
    refreshToken: null,
    idToken: "id-token",
    claims: {
      sub: "submitter-subject",
      preferredUsername: "submitter",
      tenantId: "00000000-0000-0000-0000-00000000f001",
      branchId: null,
      roles: ["payment_submitter"],
    },
    accessTokenExpiresAt: Date.now() + 60_000,
    createdAt: Date.now(),
  };
}

function makeRequest(
  url: string,
  init?: {
    method?: string;
    headers?: Record<string, string>;
    body?: BodyInit | null;
    sessionCookie?: string;
  },
): NextRequest {
  const headers = new Headers(init?.headers ?? {});
  if (init?.sessionCookie) {
    headers.set("cookie", `${SESSION_COOKIE}=${init.sessionCookie}`);
  }
  return new NextRequest(url, {
    method: init?.method ?? "POST",
    headers,
    body: init?.body ?? null,
  });
}

createSession(makeSessionRecord());

const exactBytes = Buffer.from(
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<Document>café</Document>\r\n  ",
  "utf8",
);
const exactBody = exactBytes.buffer.slice(
  exactBytes.byteOffset,
  exactBytes.byteOffset + exactBytes.byteLength,
);

let forwardCalls = 0;
async function mockForward(input: Parameters<typeof forwardPain001Upload>[0]) {
  forwardCalls += 1;
  assert.equal(input.backendBaseUrl, backendBaseUrl);
  assert.equal(input.accessToken, accessToken);
  assert.ok(input.xmlBytes instanceof ArrayBuffer);
  assert.equal(Buffer.from(input.xmlBytes).compare(exactBytes), 0);
  return {
    status: 201,
    body: "",
    contentType: null,
    location: `/api/v1/payments/${paymentId}`,
    correlationId: "corr-forward",
  };
}

async function runAuthorizedRequest(request: NextRequest) {
  forwardCalls = 0;
  return handlePain001UploadPost(request, {
    backendBaseUrl,
    forwardUpload: mockForward,
  });
}

const unauthorized = authorizeXmlUploadRequestCore(
  makeRequest("http://localhost/api/iso/pain001", {
    headers: { "content-type": "application/xml" },
  }),
);
assert.equal(unauthorized.ok, false);
if (!unauthorized.ok) {
  assert.equal(unauthorized.status, 401);
}

const missingCsrf = authorizeXmlUploadRequestCore(
  makeRequest("http://localhost/api/iso/pain001", {
    sessionCookie: sessionId,
    headers: { "content-type": "application/xml" },
  }),
);
assert.equal(missingCsrf.ok, false);
if (!missingCsrf.ok) {
  assert.equal(missingCsrf.status, 403);
}

const invalidContentType = authorizeXmlUploadRequestCore(
  makeRequest("http://localhost/api/iso/pain001", {
    sessionCookie: sessionId,
    headers: {
      "content-type": "application/json",
      [CSRF_HEADER]: csrfToken,
    },
  }),
);
assert.equal(invalidContentType.ok, false);
if (!invalidContentType.ok) {
  assert.equal(invalidContentType.status, 415);
}

const untrustedDestination = authorizeXmlUploadRequestCore(
  makeRequest("http://localhost/api/iso/pain001?backendUrl=http://evil", {
    sessionCookie: sessionId,
    headers: {
      "content-type": "application/xml",
      [CSRF_HEADER]: csrfToken,
    },
  }),
);
assert.equal(untrustedDestination.ok, false);
if (!untrustedDestination.ok) {
  assert.equal(untrustedDestination.status, 400);
}

const emptyBody = await handlePain001UploadPost(
  makeRequest("http://localhost/api/iso/pain001", {
    sessionCookie: sessionId,
    headers: {
      "content-type": "application/xml",
      [CSRF_HEADER]: csrfToken,
      [IDEMPOTENCY_KEY_HEADER]: "idem-empty",
      [SIGNER_ID_HEADER]: "00000000-0000-0000-0000-00000000e101",
      [SIGNATURE_HEADER]: "abc",
    },
    body: new Uint8Array(),
  }),
  { backendBaseUrl, forwardUpload: async () => { throw new Error("must not forward"); } },
);
assert.equal(emptyBody.status, 400);
assert.match(emptyBody.body ?? "", /Empty pain.001 payload/i);

const browserBearerRejected = await runAuthorizedRequest(
  makeRequest("http://localhost/api/iso/pain001", {
    sessionCookie: sessionId,
    headers: {
      "content-type": "application/xml",
      authorization: "Bearer browser-supplied-token",
      [CSRF_HEADER]: csrfToken,
      [IDEMPOTENCY_KEY_HEADER]: "idem-browser-bearer",
      [SIGNER_ID_HEADER]: "00000000-0000-0000-0000-00000000e101",
      [SIGNATURE_HEADER]: "abc",
      "x-tenant-id": "browser-tenant",
    },
    body: exactBody,
  }),
);
assert.equal(browserBearerRejected.status, 201);
assert.equal(forwardCalls, 1);

let capturedHeaders: Record<string, string> | undefined;
await handlePain001UploadPost(
  makeRequest("http://localhost/api/iso/pain001", {
    sessionCookie: sessionId,
    headers: {
      "content-type": "application/xml",
      authorization: "Bearer browser-supplied-token",
      [CSRF_HEADER]: csrfToken,
      [IDEMPOTENCY_KEY_HEADER]: "idem-header-proof",
      [SIGNER_ID_HEADER]: "00000000-0000-0000-0000-00000000e101",
      [SIGNATURE_HEADER]: "abc",
      "x-tenant-id": "browser-tenant",
    },
    body: exactBody,
  }),
  {
    backendBaseUrl,
    forwardUpload: async (input) => {
      capturedHeaders = {
        authorization: `Bearer ${input.accessToken}`,
        tenantHeader: "not-forwarded",
      };
      return {
        status: 201,
        body: "",
        contentType: null,
        location: `/api/v1/payments/${paymentId}`,
        correlationId: "corr-headers",
      };
    },
  },
);
assert.equal(capturedHeaders?.authorization, `Bearer ${accessToken}`);
assert.notEqual(capturedHeaders?.authorization, "Bearer browser-supplied-token");

const fixtureXml = readFileSync(join(import.meta.dirname, "../e2e/fixtures/e1-signed-pain001.xml"));
const fixtureBody = fixtureXml.buffer.slice(fixtureXml.byteOffset, fixtureXml.byteOffset + fixtureXml.byteLength);
let capturedUrl = "";
let capturedInit: RequestInit | undefined;
await forwardPain001Upload(
  {
    backendBaseUrl,
    accessToken: "token",
    xmlBytes: fixtureBody,
    idempotencyKey: "idem-2",
    signerId: "00000000-0000-0000-0000-00000000e101",
    signatureBase64: "sig",
    correlationId: "corr-2",
  },
  async (url, init) => {
    capturedUrl = String(url);
    capturedInit = init;
    return new Response(null, { status: 201, headers: { Location: `/api/v1/payments/${paymentId}` } });
  },
);
assert.equal(capturedUrl, `http://backend:8081${PAIN001_BACKEND_PATH}`);
assert.equal(capturedInit?.method, "POST");
assert.ok(capturedInit?.body instanceof ArrayBuffer);
assert.equal(Buffer.from(capturedInit.body as ArrayBuffer).compare(fixtureXml), 0);
assert.equal(buildPain001BackendUrl("http://backend:8081/"), `http://backend:8081${PAIN001_BACKEND_PATH}`);
assert.equal(paymentIdFromBackendLocation(`/api/v1/payments/${paymentId}`), paymentId);
assert.equal(frontendPaymentDetailPath(paymentId), `/payments/${paymentId}`);

const left = new Uint8Array([1, 2, 3]).buffer;
const right = new Uint8Array([1, 2, 3]).buffer;
const different = new Uint8Array([1, 2, 4]).buffer;
assert.equal(buffersEqual(left, right), true);
assert.equal(buffersEqual(left, different), false);

console.log("E1 pain001 upload route boundary verified");
