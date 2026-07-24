import { randomUUID } from "node:crypto";
import {
  CORRELATION_ID_HEADER,
  forwardPain001Upload,
  IDEMPOTENCY_KEY_HEADER,
  SIGNATURE_ALGO_HEADER,
  SIGNATURE_HEADER,
  SIGNER_ID_HEADER,
  type Pain001UploadForwardResult,
} from "./pain001-upload.ts";
import { authorizeXmlUploadRequestCore } from "./pain001-upload-auth.ts";
import type { NextRequest } from "next/server";

export interface Pain001UploadRouteResult {
  status: number;
  body: string | null;
  headers: Record<string, string>;
}

export interface Pain001UploadRouteDeps {
  backendBaseUrl: string;
  forwardUpload?: (
    input: Parameters<typeof forwardPain001Upload>[0],
  ) => Promise<Pain001UploadForwardResult>;
}

function responseFromBackend(backendResult: Pain001UploadForwardResult): Pain001UploadRouteResult {
  const headers: Record<string, string> = {
    [CORRELATION_ID_HEADER]: backendResult.correlationId,
  };
  if (backendResult.contentType) {
    headers["Content-Type"] = backendResult.contentType;
  }
  if (backendResult.location) {
    headers.Location = backendResult.location;
  }
  return {
    status: backendResult.status,
    body: backendResult.body.length > 0 ? backendResult.body : null,
    headers,
  };
}

function problemResponse(status: number, title: string): Pain001UploadRouteResult {
  return {
    status,
    body: JSON.stringify({ type: "about:blank", title, status }),
    headers: { "Content-Type": "application/json" },
  };
}

export async function handlePain001UploadPost(
  request: NextRequest,
  deps: Pain001UploadRouteDeps,
): Promise<Pain001UploadRouteResult> {
  const authz = authorizeXmlUploadRequestCore(request);
  if (!authz.ok) {
    return {
      status: authz.status,
      body: JSON.stringify({ type: "about:blank", title: authz.title, status: authz.status }),
      headers: { "Content-Type": "application/json" },
    };
  }

  const idempotencyKey = request.headers.get(IDEMPOTENCY_KEY_HEADER);
  if (!idempotencyKey) {
    return problemResponse(400, "Missing Idempotency-Key header");
  }

  const signerId = request.headers.get(SIGNER_ID_HEADER);
  const signatureBase64 = request.headers.get(SIGNATURE_HEADER);
  if (!signerId || !signatureBase64) {
    return problemResponse(400, "Missing signature transport metadata");
  }

  const xmlBytes = await request.arrayBuffer();
  if (xmlBytes.byteLength === 0) {
    return problemResponse(400, "Empty pain.001 payload");
  }

  const correlationId = request.headers.get(CORRELATION_ID_HEADER) ?? randomUUID();
  const forwardUpload = deps.forwardUpload ?? forwardPain001Upload;
  const backendResult = await forwardUpload({
    backendBaseUrl: deps.backendBaseUrl,
    accessToken: authz.session.accessToken,
    xmlBytes,
    idempotencyKey,
    signerId,
    signatureBase64,
    signatureAlgo: request.headers.get(SIGNATURE_ALGO_HEADER) ?? "Ed25519",
    correlationId,
  });

  return responseFromBackend(backendResult);
}
