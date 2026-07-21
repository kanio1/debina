# Business Capability Map

The authoritative structured capability record is this document; the Mermaid view is a navigation aid.

| Group | Capabilities | Classification | Current coverage |
|---|---|---|---|
| Initiate and control | payment initiation, file/bulk initiation, validation, signature, approval | core; file/bulk rail-specific | single and signed initiation, approval, signature implemented; file grouping future |
| Process payment meaning | ISO processing/correlation, lifecycle, routing, status | core | implemented in payment/ISO/routing, inbound status completion partial |
| Clear and settle | clearing submission, settlement, liquidity, ledger, finality | core; rail-specific | gross instant and deferred cycle core implemented; CSM submission is future |
| Resolve exceptions | reject, return, recall, case management, reconciliation | supporting; rail-specific | concepts/use cases future; no claim of implementation |
| Deliver and evidence | egress, delivery, audit, evidence, reporting, investigation | supporting | outbound queue and audit implemented; full rendering/delivery/reporting partial/future |
| Govern and operate | reference data, identity/access, risk/verification, simulation | generic/platform; simulation-only where noted | reference data/security partial; risk/simulation future |

Classification does not imply an aggregate, package or module.
