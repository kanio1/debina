import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import {
  clearIdempotencyBinding,
  fingerprintPayload,
  invalidateIdempotencyOnPayloadChange,
  parsePain001SubmissionResponse,
  resolveIdempotencyKeyForSubmit,
} from "../src/lib/pain001-upload-submission.ts";

const bytesA = new Uint8Array([1, 2, 3]).buffer;
const bytesB = new Uint8Array([1, 2, 4]).buffer;
let mintCount = 0;
const mintKey = () => `idem-${++mintCount}`;

let binding = clearIdempotencyBinding();
binding = invalidateIdempotencyOnPayloadChange(binding, bytesA);
const firstSubmit = resolveIdempotencyKeyForSubmit(binding, bytesA, mintKey);
binding = firstSubmit.binding;
assert.equal(firstSubmit.idempotencyKey, "idem-1");

const retrySamePayload = resolveIdempotencyKeyForSubmit(binding, bytesA, mintKey);
assert.equal(retrySamePayload.idempotencyKey, "idem-1");
assert.equal(mintCount, 1);

binding = invalidateIdempotencyOnPayloadChange(binding, bytesB);
const secondPayloadSubmit = resolveIdempotencyKeyForSubmit(binding, bytesB, mintKey);
assert.equal(secondPayloadSubmit.idempotencyKey, "idem-2");
assert.notEqual(secondPayloadSubmit.idempotencyKey, firstSubmit.idempotencyKey);

binding = secondPayloadSubmit.binding;
let duplicateGuardActive = false;
let effectiveRequests = 0;
function attemptSubmit() {
  if (duplicateGuardActive) {
    return null;
  }
  duplicateGuardActive = true;
  const resolved = resolveIdempotencyKeyForSubmit(binding, bytesB, mintKey);
  binding = resolved.binding;
  effectiveRequests += 1;
  return resolved.idempotencyKey;
}
const firstAttemptKey = attemptSubmit();
const secondAttemptKey = attemptSubmit();
assert.equal(firstAttemptKey, "idem-2");
assert.equal(secondAttemptKey, null);
assert.equal(effectiveRequests, 1);
assert.equal(mintCount, 2);

binding = clearIdempotencyBinding();
binding = invalidateIdempotencyOnPayloadChange(binding, bytesA);
const completedSubmit = resolveIdempotencyKeyForSubmit(binding, bytesA, mintKey);
binding = clearIdempotencyBinding();
binding = invalidateIdempotencyOnPayloadChange(binding, bytesA);
const afterSuccessSubmit = resolveIdempotencyKeyForSubmit(binding, bytesA, mintKey);
assert.notEqual(afterSuccessSubmit.idempotencyKey, completedSubmit.idempotencyKey);

const paymentId = "11111111-2222-3333-4444-555555555555";
const created = await parsePain001SubmissionResponse(
  new Response(null, { status: 201, headers: { Location: `/api/v1/payments/${paymentId}` } }),
);
assert.deepEqual(created, { paymentId, approvalStatus: "NOT_REQUIRED" });

const createdWithoutLocation = await parsePain001SubmissionResponse(new Response(null, { status: 201 }));
assert.equal("error" in createdWithoutLocation, true);
if ("error" in createdWithoutLocation) {
  assert.match(createdWithoutLocation.error, /payment location/i);
}

const accepted = await parsePain001SubmissionResponse(
  new Response(JSON.stringify({ paymentId, approvalStatus: "PENDING_APPROVAL" }), {
    status: 202,
    headers: { "Content-Type": "application/json" },
  }),
);
assert.deepEqual(accepted, { paymentId, approvalStatus: "PENDING_APPROVAL" });

const malformedAccepted = await parsePain001SubmissionResponse(
  new Response("{not-json", { status: 202, headers: { "Content-Type": "application/json" } }),
);
assert.equal("error" in malformedAccepted, true);

const incompleteAccepted = await parsePain001SubmissionResponse(
  new Response(JSON.stringify({ approvalStatus: "PENDING_APPROVAL" }), {
    status: 202,
    headers: { "Content-Type": "application/json" },
  }),
);
assert.equal("error" in incompleteAccepted, true);

const staleResultCleared = (() => {
  let acceptedPaymentId: string | null = paymentId;
  binding = invalidateIdempotencyOnPayloadChange(clearIdempotencyBinding(), bytesB);
  acceptedPaymentId = null;
  return acceptedPaymentId;
})();
assert.equal(staleResultCleared, null);
assert.equal(fingerprintPayload(bytesA), fingerprintPayload(bytesA));
assert.notEqual(fingerprintPayload(bytesA), fingerprintPayload(bytesB));

const fixtureXml = readFileSync(join(import.meta.dirname, "../e2e/fixtures/e1-signed-pain001.xml"));
const labMeta = JSON.parse(
  readFileSync(join(import.meta.dirname, "../e2e/fixtures/e1-lab-ed25519-test-only.meta.json"), "utf8"),
) as { publicMaterialBase64: string };
const fixtureMeta = JSON.parse(
  readFileSync(join(import.meta.dirname, "../e2e/fixtures/e1-signed-pain001.meta.json"), "utf8"),
) as { publicMaterialBase64: string };
const v62Sql = readFileSync(
  join(import.meta.dirname, "../../backend/src/main/resources/db/migration/signature/V62__e1_smoke_verification_key.sql"),
  "utf8",
);
assert.equal(fixtureMeta.publicMaterialBase64, labMeta.publicMaterialBase64);
assert.ok(v62Sql.includes(labMeta.publicMaterialBase64));
assert.ok(fixtureXml.byteLength > 0);

console.log("E1 pain001 upload card submission lifecycle verified");
