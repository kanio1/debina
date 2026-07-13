# SEPA Nexus — Signature Module Blueprint

**Scope.** Full design of the `signature` module — the last named-but-undesigned boundary before Iteration 0 (decision-gate blocker B2). Closes `[SECURITY-GAP]`/`[DB-GAP]` R-13. Consistent with the ownership integration patch (§3.6.1 rule 11, the `signature` ownership summary, §3.6.4/§3.6.5 tests) and the main blueprint's G1 verify-before-parse rule. `[NO-CODE]` — design sketches and table summaries only, no production DDL, no application code. Does not reopen ADR-N1…N8.
**One-line purpose.** `signature` proves or produces a cryptographic signature over **raw bytes**, records the verdict as evidence, and does nothing else — it is a security *boundary*, never a business decision-maker.

---

## 1. Executive Verdict

`signature` is a **Supporting** module owning exactly two aggregates (`MessageSignature`, `SignatureKey`) and one schema. It exposes four ports and touches no other schema. Its whole reason to exist is to make the **verify-before-parse** rule (G1) and **outbound signing** (egress) into a testable, owned boundary rather than a filter buried in `ingress`. `[FREEZE]` The single most important invariant: **a `FAILED` verdict is data, not a decision** — `signature` records that verification failed and returns it; the *rejection* of the payment is `ingress`'s action, driven by that verdict. `signature` never rejects a payment, never sets a status, never writes a domain table, never delivers anything. It is `[MVP]` for verification (Iteration 2, when signed channels and status-out first matter) and `[MVP]` for signing (Iteration 5, when outbound ISO artifacts are signed); a thin verdict-recording slice can land as early as Iteration 1 if a signed channel is demoed, but nothing in Iteration 1's JSON-only spine requires it.

Synthetic by design `[ADOPT]`: this is a **Szafir-like / XAdES-like educational** signer, not production PKI. It makes no compliance claim. That is a feature — it lets the lab teach signature evidence, key rotation, and tamper detection deterministically, seeded, without an HSM.

---

## 2. Module Responsibility

`signature` **owns**:
- raw-byte signature verification (detached or enveloped, over the exact archived bytes — never over a re-serialized form);
- file and message signature verification (same mechanism; a file is just a larger byte array);
- signature verification **verdicts** (`VERIFIED` / `FAILED` / `NOT_APPLICABLE`) with reason and key reference;
- outbound artifact **signing** for `egress` (detached signature over the rendered artifact bytes);
- a **synthetic key registry** (key material, validity window, owner participant, algorithm);
- verify/sign **ports** and a verdict-evidence port;
- a **testable security boundary** — every rule above is an ArchUnit/SQL/ordering test, not a comment.

`signature` **must not** `[REJECT]`:
- parse ISO (that is `iso-adapter`; `signature` sees bytes, not `pain.001`);
- decide payment status or business outcome;
- mutate `payment`, `settlement`, `ledger`, `egress`, `iso`, `case`, `reconciliation`;
- deliver outbound artifacts (that is `egress`'s transport layer — `signature` only signs);
- manage or configure Keycloak (that is `identity-access`);
- decide finality.

---

## 3. Ownership Boundary

`[DB-OWNERSHIP]` One module, one schema (`signature`), one DB role (`signature_role`), sole writer. Reaffirms the ownership integration rows:

| Aspect | Rule |
|---|---|
| Owns schema | `signature` — `message_signatures`, `signature_keys`, `signature_verification_events` |
| Sole writer | `signature_role`; no other module role has INSERT/UPDATE on `signature.*` |
| Reached via | ports only (§4); **no non-`signature` module may access a `signature` repository** (ArchUnit) |
| Verdict delivery | pushed to the caller as a **port return value** and recorded as evidence — other modules do **not** read `signature.*` directly to learn a verdict |
| Callers | `ingress` (verify, before parse), `egress` (sign, via port), `evidence-audit` (receives verdict as evidence) |
| Forbidden | any write to `payment`/`settlement`/`ledger`/`egress`/`iso`/`case`/`reconciliation`; ISO parsing; status decisions; delivery |

The verify-before-parse ordering is a **dependency-graph rule**, not just filter config: `iso-adapter` parses **only after** the verdict is recorded on the archived raw message (§3.6.4 forbidden-access list, ownership patch).

---

## 4. Ports and Adapters

Hexagonal: inbound ports are called by `ingress`/`egress`; outbound ports let `signature` record evidence and read keys. No port exposes a repository or a domain table.

| Port | Direction | Used By | Purpose | MVP/P1/P2 |
|---|---|---|---|---|
| `SignatureVerificationPort` | inbound (into `signature`) | `ingress` | `verify(rawBytes, channel, declaredSigner?) → Verdict{result, keyId, algo, reason}`; over exact archived bytes | `[MVP]` (Iter 2) |
| `SignatureSigningPort` | inbound (into `signature`) | `egress` | `sign(artifactBytes, signingKeyRef) → DetachedSignature{bytes, keyId, algo}`; detached, egress attaches+delivers | `[MVP]` (Iter 5) |
| `SignatureEvidencePort` | outbound (from `signature`) | → `evidence-audit` | records the verdict/signing event as an immutable evidence record in the **same transaction** as the verdict write | `[MVP]` |
| `KeyRegistryPort` | inbound (into `signature`) | admin (`security_admin`/`key_admin`), internal | register/rotate/expire a synthetic key; look up a key for verify/sign by `(participant, purpose, as_of)` | `[MVP]` (register/lookup) / `[P1]` (rotation UI) |

Adapter notes: `ingress` calls `SignatureVerificationPort` from inside the pinned filter chain (`RawBodyCachingFilter` → `MessageSignatureVerificationFilter` → controller). `egress` calls `SignatureSigningPort` only when a resolved egress profile snapshot has `signing_required = true`. Neither adapter reads `signature.*` tables.

---

## 5. Data Model Sketch

`[NO-CODE]` Table summary only; full DDL lands with EPIC-OWN-10 in the iteration that implements verification.

| Table | Owner | Purpose | Key Columns | MVP/P1/P2 |
|---|---|---|---|---|
| `signature.message_signatures` | `signature` | one row per signature verified or produced, linked to the raw/artifact bytes it covers | `id` (uuidv7 PK), `raw_message_id` (FK → `ingress.raw_inbound_messages`, nullable for outbound), `outbound_artifact_id` (nullable, for signing), `direction` (INBOUND/OUTBOUND), `algo`, `key_id` (FK → `signature_keys`), `signature_bytes`, `covered_sha256`, `created_at` | `[MVP]` |
| `signature.signature_keys` | `signature` | synthetic key registry: material + validity + owner + purpose | `id` (PK), `participant_id` (FK → `reference_data.participants`, nullable for platform keys), `purpose` (VERIFY/SIGN/BOTH), `algo`, `public_material`, `private_material_ref` (synthetic, lab-only), `valid_from`, `valid_to`, `status` (ACTIVE/EXPIRED/REVOKED) | `[MVP]` |
| `signature.signature_verification_events` | `signature` | append-only verdict log: every verify attempt and its outcome | `id` (PK), `raw_message_id` (FK), `verdict` (VERIFIED/FAILED/NOT_APPLICABLE), `reason_code`, `key_id` (nullable if unknown key), `channel`, `verified_at` | `[MVP]` |

Notes: `message_signatures.covered_sha256` binds the signature to the exact bytes (tamper detection is a byte-hash mismatch). `signature_keys.private_material_ref` is **synthetic** — a lab key store, never a real HSM handle; the blueprint states this so no reader mistakes it for production key custody. Verdict is written to `signature_verification_events` **and** pushed via `SignatureEvidencePort` in one transaction (§9).

---

## 6. Inbound Verification Flow

`[OPERATOR-WORKFLOW]` invisible to operators — this runs inside ingress. For **signed channels** (bank file, signed XML message); JSON-direct and unsigned channels get `NOT_APPLICABLE`.

```text
Signed channel request (raw bytes, declared signer, Idempotency-Key)
  → RawBodyCachingFilter: cache exact raw bytes (G1 — bytes are archived before anything touches them)
  → MessageSignatureVerificationFilter:
       → SignatureVerificationPort.verify(rawBytes, channel, declaredSigner)
             → KeyRegistryPort.lookup(declaredSigner, purpose=VERIFY, as_of=now)
                  unknown/expired/revoked key → Verdict{FAILED, reason=KEY_*}
             → recompute covered_sha256; compare signature against key
                  mismatch → Verdict{FAILED, reason=TAMPERED_OR_INVALID}
                  match    → Verdict{VERIFIED, keyId, algo}
             → write signature.signature_verification_events (+ message_signatures on VERIFIED)
             → SignatureEvidencePort.record(verdict)              [same TX]
       → verdict = FAILED  → ingress REJECTS pre-parse (raw kept, iso.message.rejected, NO payment, NO parse)
       → verdict = VERIFIED or NOT_APPLICABLE → continue to controller → idempotency → iso-adapter parse
```

`[FREEZE]` **XML parse is never executed before the verdict is recorded on a signed channel.** The rejection on `FAILED` is `ingress`'s action; `signature` only supplied the verdict. `iso-adapter` sees the payload only after a non-`FAILED` verdict already sits on the raw message.

---

## 7. Outbound Signing Flow

Runs inside `egress`, only when the resolved profile requires signing.

```text
egress: artifact RENDERED (bytes ready, e.g. pacs.002 / camt.029 / pacs.004)
  → resolve egress profile snapshot: signing_required?
       false → skip; state stays RENDERED → CLAIMED_FOR_DELIVERY (unsigned channel)
       true  → SignatureSigningPort.sign(artifactBytes, signingKeyRef)
                    → KeyRegistryPort.lookup(platform/participant signing key, purpose=SIGN, as_of=now)
                    → produce DetachedSignature{bytes, keyId, algo}
                    → write signature.message_signatures(direction=OUTBOUND, outbound_artifact_id, covered_sha256)
                    → SignatureEvidencePort.record(signing event)     [same TX]
       → return detached signature to egress
  → egress attaches signature to the artifact envelope and moves state RENDERED → SIGNED → CLAIMED_FOR_DELIVERY → ...
```

`[FREEZE]` `signature` produces a **detached** signature and returns it. **`egress` attaches and delivers** — `signature` never touches transport, never sets `SIGNED`/`DELIVERED` (those are egress transport states), never decides finality.

---

## 8. Key Registry Model

`[ADOPT]` Synthetic, seeded, deterministic. A key is `(participant | platform, purpose, algo, validity window, status)`.

- **Lookup** is always `(who, purpose, as_of)` → the one ACTIVE key valid at `as_of`; no ambient "current key" — determinism requires an explicit `as_of` (aligns with the platform-wide `ClockPort`/`as_of` discipline).
- **Rotation** = expire the old key (`valid_to`), register a new ACTIVE key; historical verdicts still reference the key that was valid then (immutable evidence).
- **Expired/revoked** keys verify **nothing** — a signature under an expired key is `FAILED{KEY_EXPIRED}`, not a soft warning. This is a first-class negative test.
- **Seeding**: platform + a few participant keys are seeded at bootstrap so the simulator can produce validly-signed and deliberately-tampered fixtures deterministically.
- No real HSM, no real CA chain, no OCSP `[REJECT]` for MVP — `[P2]` educational track only if ever wanted.

---

## 9. Evidence and Audit Integration

Every verify and every sign writes its verdict/event **and** records evidence through `SignatureEvidencePort` → `evidence-audit`, in the **same database transaction** as the `signature.*` write (the platform's same-transaction audit rule, G9b). Consequences: a verdict can never exist without its evidence row, and vice versa; the evidence viewer (frontend) surfaces the verdict badge from this evidence, not by reading `signature.*` directly. `signature` is a producer to `evidence-audit`, never a reader of other modules' data.

---

## 10. Security Risks and Non-Goals

| Risk / Non-Goal | Stance |
|---|---|
| Treating a verdict as a business decision | `[REJECT]` — verdict is data; rejection is `ingress`'s action |
| Verifying a re-serialized (not raw) form | `[REJECT]` — always the exact archived bytes; `covered_sha256` binds them |
| Parsing before verifying on signed channels | `[REJECT]` — enforced as an ordering test (G1) |
| Real HSM / CA / OCSP / production PKI | Non-goal `[P2]` — synthetic lab signer only, no compliance claim |
| Key custody realism | Non-goal — `private_material_ref` is a lab store, explicitly synthetic |
| `signature` delivering or storing outbound artifacts | `[REJECT]` — egress owns transport |
| Finality/status opinions | `[REJECT]` — out of scope by construction |
| Signature-stripping / downgrade (unsigned accepted where signing required) | Guarded: channel policy makes verification **required** on bank/file channels; a missing signature there is `FAILED`, not `NOT_APPLICABLE` |

---

## 11. Tests

`[PLAYWRIGHT]` mostly backend; one frontend badge assertion. Maps to EPIC-OWN-10.

- **verify-before-parse order test** — on a signed channel, assert `iso-adapter` parse is not invoked until the verdict is recorded (ordering/interaction test on the filter chain).
- **tampered payload fails** — flip one byte of a validly-signed fixture → `FAILED{TAMPERED_OR_INVALID}`, no payment created.
- **unknown key fails** — signature under a key not in the registry → `FAILED{KEY_UNKNOWN}`.
- **expired key fails** — valid signature under a key past `valid_to` → `FAILED{KEY_EXPIRED}`.
- **signature verdict stored** — after verify, exactly one `signature_verification_events` row + one evidence row exist, in the same TX.
- **`signature` does not mutate domain tables** — grant sweep: `signature_role` has zero write grant on `payment`/`settlement`/`ledger`/`egress`/`iso`/`case`/`reconciliation`.
- **egress uses `SignatureSigningPort`** — egress signs via the port; no direct `signature.*` access from egress.
- **non-signature modules cannot access `signature` repositories directly** — ArchUnit: no repository reference to `signature` internals outside the module.
- **XML parse not executed before verification on signed channels** — negative: a `FAILED` verdict path never reaches the parser (asserted via no `iso.message.parsed` for that raw message, only `iso.message.rejected`).
- **required-channel missing-signature fails** — bank/file channel with no signature → `FAILED`, not `NOT_APPLICABLE`.
- **detached-signature round-trip** — `sign()` output verifies clean via `verify()` under the same key.

---

## 12. MVP / P1 / P2 Scope

| Capability | Scope | Reason |
|---|---|---|
| Verify on raw bytes + verdict recording + evidence | `[MVP]` (Iter 2) | verify-before-parse is a founding security rule; needed once signed channels/status-out matter |
| Synthetic key registry (register + lookup by `as_of`) | `[MVP]` | verification cannot work without keys |
| Detached signing for egress | `[MVP]` (Iter 5) | outbound ISO artifacts are signed from Iteration 5 |
| Channel policy (bank/file = verification required) | `[MVP]` | prevents signature-downgrade |
| Key rotation admin UI + rotation flow | `[P1]` | rotation mechanics are a good lesson but not blocking |
| Multiple algorithms / algorithm negotiation | `[P1]` | one synthetic algorithm suffices for MVP |
| Real HSM/CA/OCSP/timestamping | `[P2]` (or never) | production PKI is a non-goal; synthetic only |

---

## 13. Iteration 0 / Iteration 1 / Iteration 2 Impact

- **Iteration 0:** create the `signature` schema stub + `signature_role` + the ArchUnit rule (no foreign repo access to `signature`) + the grant-sweep test skeleton, alongside the other module stubs. **No verification logic yet** — just the boundary and its gates, so later work cannot violate them. The `RawBodyCachingFilter` bean can land here (it is generic raw-byte caching) even though verification does not.
- **Iteration 1:** no hard dependency — the JSON-direct spine uses `NOT_APPLICABLE` verdicts (JSON channel is unsigned). If a signed-channel demo is wanted early, the thin verdict-recording slice (verify + `signature_verification_events` + evidence) can land here; otherwise it waits.
- **Iteration 2:** full inbound verification goes live (signed channels + status-out context). `egress` gains the `SignatureSigningPort` call site (guarded by `signing_required`), with actual signing wired in Iteration 5. This is the iteration where B2 must be fully closed, because egress status-out assumes a real signing port exists.

---

*End of signature module blueprint. `[NO-CODE]` — sketches only; full DDL and adapters land per EPIC-OWN-10 in the owning iteration. Consistent with ADR-N1…N8, the ownership integration patch, and the main blueprint G1 rule. Synthetic signer — no production PKI or compliance claim.*
