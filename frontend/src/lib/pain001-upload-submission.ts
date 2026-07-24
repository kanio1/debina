import { paymentIdFromBackendLocation } from "./pain001-upload.ts";

export interface IdempotencyBinding {
  payloadFingerprint: string | null;
  idempotencyKey: string | null;
}

export interface SubmissionAcceptedResult {
  paymentId: string;
  approvalStatus: string;
}

export interface SubmissionErrorResult {
  error: string;
}

export type SubmissionParseResult = SubmissionAcceptedResult | SubmissionErrorResult;

export function fingerprintPayload(xmlBytes: ArrayBuffer): string {
  const view = new Uint8Array(xmlBytes);
  let hash = 2166136261;
  for (let index = 0; index < view.length; index += 1) {
    hash ^= view[index]!;
    hash = Math.imul(hash, 16777619);
  }
  return `${view.byteLength}:${(hash >>> 0).toString(16)}`;
}

export function invalidateIdempotencyOnPayloadChange(
  binding: IdempotencyBinding,
  xmlBytes: ArrayBuffer | null,
): IdempotencyBinding {
  if (!xmlBytes || xmlBytes.byteLength === 0) {
    return { payloadFingerprint: null, idempotencyKey: null };
  }
  const fingerprint = fingerprintPayload(xmlBytes);
  if (binding.payloadFingerprint !== null && binding.payloadFingerprint !== fingerprint) {
    return { payloadFingerprint: fingerprint, idempotencyKey: null };
  }
  return { ...binding, payloadFingerprint: fingerprint };
}

export function resolveIdempotencyKeyForSubmit(
  binding: IdempotencyBinding,
  xmlBytes: ArrayBuffer,
  mintKey: () => string,
): { binding: IdempotencyBinding; idempotencyKey: string } {
  const fingerprint = fingerprintPayload(xmlBytes);
  if (binding.payloadFingerprint === fingerprint && binding.idempotencyKey) {
    return { binding, idempotencyKey: binding.idempotencyKey };
  }
  const idempotencyKey = mintKey();
  return {
    binding: { payloadFingerprint: fingerprint, idempotencyKey },
    idempotencyKey,
  };
}

export function clearIdempotencyBinding(): IdempotencyBinding {
  return { payloadFingerprint: null, idempotencyKey: null };
}

interface SubmissionAcceptedBody {
  paymentId?: string;
  approvalStatus?: string;
}

export async function parsePain001SubmissionResponse(
  response: Response,
): Promise<SubmissionParseResult> {
  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as { detail?: string; title?: string } | null;
    return {
      error: problem?.detail ?? problem?.title ?? `pain.001 submission failed (${response.status})`,
    };
  }

  const locationPaymentId = paymentIdFromBackendLocation(response.headers.get("Location"));

  if (response.status === 202) {
    const body = (await response.json().catch(() => null)) as SubmissionAcceptedBody | null;
    const paymentId = body?.paymentId ?? locationPaymentId;
    if (!paymentId) {
      return { error: "Accepted submission did not include a payment identifier." };
    }
    const approvalStatus = body?.approvalStatus ?? "PENDING_APPROVAL";
    if (approvalStatus !== "PENDING_APPROVAL") {
      return { error: "Accepted submission returned an unexpected approval status." };
    }
    return { paymentId, approvalStatus };
  }

  if (response.status === 201) {
    if (!locationPaymentId) {
      return { error: "Created submission did not include a payment location." };
    }
    return { paymentId: locationPaymentId, approvalStatus: "NOT_REQUIRED" };
  }

  return { error: `pain.001 submission returned an unexpected status (${response.status}).` };
}
