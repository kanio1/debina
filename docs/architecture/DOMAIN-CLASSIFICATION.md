# Domain Classification

| Logical area | Classification | Subdomain | Rationale |
|---|---|---|---|
| payment lifecycle, routing, settlement, ledger, finality | bounded contexts | core | source-owned business invariants and schemas |
| ingress, file ingestion, signature, ISO adaptation/validation/correlation, egress, clearing submission | technical/integration adapters | supporting | translate or transport without owning universal payment meaning |
| approval, reconciliation, case management, evidence/audit, reporting | supporting subsystems / read-model host | supporting | distinct operational policies; several are not implemented |
| liquidity | ledger/settlement responsibility, not separate context now | core | LedgerPort owns reservations; settlement coordinates attempts |
| reference data, identity/access | generic subsystem | generic | configuration and authentication/authorization boundary |
| risk, simulation, local verification automation | platform component | supporting | simulation/verification are project capabilities, not payment contexts |
| GraphQL transport, Next.js BFF | technical adapter | generic | Query-only/read path and session boundary; no domain data ownership |

The classification does not equate packages, schemas or Spring modules to bounded contexts.
