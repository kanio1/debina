import { randomUUID } from "node:crypto";
import { timingSafeEqual } from "node:crypto";

export const PAIN001_BACKEND_PATH = "/api/v1/iso/pain001";
export const CORRELATION_ID_HEADER = "X-Correlation-Id";
export const IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
export const SIGNER_ID_HEADER = "X-Signer-Id";
export const SIGNATURE_HEADER = "X-Signature";
export const SIGNATURE_ALGO_HEADER = "X-Signature-Algo";

export interface Pain001UploadForwardInput {
  backendBaseUrl: string;
  accessToken: string;
  xmlBytes: ArrayBuffer;
  idempotencyKey: string;
  signerId: string;
  signatureBase64: string;
  signatureAlgo?: string;
  correlationId?: string;
}

export interface Pain001UploadForwardResult {
  status: number;
  body: string;
  contentType: string | null;
  location: string | null;
  correlationId: string;
}

export function buildPain001BackendUrl(backendBaseUrl: string): string {
  const base = backendBaseUrl.endsWith("/") ? backendBaseUrl.slice(0, -1) : backendBaseUrl;
  return `${base}${PAIN001_BACKEND_PATH}`;
}

export function buildPain001ForwardHeaders(input: Pain001UploadForwardInput): Record<string, string> {
  const headers: Record<string, string> = {
    Authorization: `Bearer ${input.accessToken}`,
    "Content-Type": "application/xml",
    [IDEMPOTENCY_KEY_HEADER]: input.idempotencyKey,
    [SIGNER_ID_HEADER]: input.signerId,
    [SIGNATURE_HEADER]: input.signatureBase64,
    [SIGNATURE_ALGO_HEADER]: input.signatureAlgo ?? "Ed25519",
    [CORRELATION_ID_HEADER]: input.correlationId ?? randomUUID(),
  };
  return headers;
}

export async function forwardPain001Upload(
  input: Pain001UploadForwardInput,
  fetchImpl: typeof fetch = fetch,
): Promise<Pain001UploadForwardResult> {
  const headers = buildPain001ForwardHeaders(input);
  const backendResponse = await fetchImpl(buildPain001BackendUrl(input.backendBaseUrl), {
    method: "POST",
    headers,
    body: input.xmlBytes,
  });

  const responseBody = await backendResponse.text();
  return {
    status: backendResponse.status,
    body: responseBody,
    contentType: backendResponse.headers.get("content-type"),
    location: backendResponse.headers.get("location"),
    correlationId: headers[CORRELATION_ID_HEADER],
  };
}

export function paymentIdFromBackendLocation(location: string | null): string | null {
  if (!location) {
    return null;
  }
  const match = location.match(/\/api\/v1\/payments\/([0-9a-f-]{36})$/i);
  return match?.[1] ?? null;
}

export function frontendPaymentDetailPath(paymentId: string): string {
  return `/payments/${paymentId}`;
}

const XML_CONTENT_TYPE = "application/xml";
const UNTRUSTED_BACKEND_QUERY_KEYS = ["backendUrl", "backend_url", "targetUrl", "target_url"];

export function isXmlContentType(contentType: string): boolean {
  const normalized = contentType.split(";")[0]?.trim().toLowerCase();
  return normalized === XML_CONTENT_TYPE;
}

export function rejectsUntrustedBackendDestination(request: { nextUrl: URL }): boolean {
  return UNTRUSTED_BACKEND_QUERY_KEYS.some((key) => request.nextUrl.searchParams.has(key));
}

export function buffersEqual(left: ArrayBuffer, right: ArrayBuffer): boolean {
  const a = Buffer.from(left);
  const b = Buffer.from(right);
  if (a.length !== b.length) {
    return false;
  }
  return timingSafeEqual(a, b);
}
