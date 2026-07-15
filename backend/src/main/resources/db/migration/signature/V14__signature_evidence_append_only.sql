-- owner: signature [MVP] — Story 31.2, append-only enforcement for verdict/signature evidence
-- V13 blanket-granted UPDATE (matching the existing ingress/reference_data convention) on all
-- three tables. That grant is right for signature_keys — rotation legitimately expires a key by
-- updating valid_to (blueprint §8) — but wrong for the two true append-only evidence logs: every
-- verify attempt is a new signature_verification_events row (§5, "every verify attempt and its
-- outcome"), never an update to a prior one, and message_signatures is immutable evidence once
-- written. Does not edit V13 (already applied) — narrows the grant instead.

REVOKE UPDATE, DELETE ON signature.message_signatures FROM signature_role;
REVOKE UPDATE, DELETE ON signature.signature_verification_events FROM signature_role;
