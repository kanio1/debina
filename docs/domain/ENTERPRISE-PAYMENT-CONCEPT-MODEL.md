# Enterprise Payment Concept Model

The authoritative records are [PAYMENT-CONCEPT-CATALOG.yaml](PAYMENT-CONCEPT-CATALOG.yaml); this guide prevents category errors. `PaymentOrder` is the Debina lifecycle record currently represented by `payment.payments`; `PaymentInstruction` is a source/business-facing instruction and is not presently a separately persisted aggregate. A message, its application header, its envelope, a file and a transport artifact are distinct. They do not become aggregates by naming them.

The model keeps business lifecycle, ISO/message, settlement finality, transport/delivery and receipt/reconciliation status axes separate. A return after finality is a new opposite-direction payment, not a ledger reversal. [PROJECT-ADR: project-adr-n10]

## Aggregate-admission review

| Concept | Result | Evidence and recommendation |
|---|---|---|
| PaymentOrder | CURRENT-REPRESENTATION-SUFFICIENT | independent identity/lifecycle/commands/invariants exist in `payment.payments`; do not split absent a use-case and source-backed invariant |
| PaymentInstruction | DEFER | external/business instruction has identity but no demonstrated separate command, consistency or recovery boundary |
| PaymentGroup | INSUFFICIENT_EVIDENCE | ISO group semantics exist, but no file/group invariant or persistence requirement is implemented; define in file-use-case first |
| PaymentBatch | RAIL_SPECIFIC | do not normalize file, CSM submission and settlement batch; admit only with rail-specific invariant evidence |
| ClearingSubmission | RAIL_SPECIFIC | independent lifecycle likely only where applicable CSM evidence and submission recovery contract exist |
| TransportFile | DO-NOT-ADMIT | technical artifact; egress can own transport state without a business aggregate unless file recovery invariant is proven |
| Case | DEFER | likely independent lifecycle/commands, but its business rule/source profile and owner are not built |
| SettlementCycle | ADMIT | independent identity, cycle commands, positions, recovery and persistence exist in `settlement.settlement_cycles` [PROJECT-ADR: ADR-N13] |

Admission result changes neither code nor frozen decisions.
