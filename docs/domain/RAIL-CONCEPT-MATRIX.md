# Rail Concept Matrix

Status values: `SUPPORTED`, `NOT-APPLICABLE`, `RAIL_SPECIFIC`, `PROJECT_SIMULATION`, `UNKNOWN`, `PARTICIPANT_DOCUMENTATION_REQUIRED`, `CONFLICTING_SOURCES`. Sources: `epc-sct`, `epc-sct-inst`, `step2-public`, `rt1-public`, `tips-public`, `stet-public` in the registry. Public landing pages are insufficient for participant-only details.

| Aspect | generic SCT | STEP2 SCT | generic SCT Inst | RT1 | TIPS | STET |
|---|---|---|---|---|---|---|
| primary processing unit | SUPPORTED: credit transfer | RAIL_SPECIFIC | SUPPORTED: instant credit transfer | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC |
| single vs multiple transactions | RAIL_SPECIFIC | RAIL_SPECIFIC | SUPPORTED: transaction intent | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC |
| group/batch/bulk/file | SUPPORTED only where scheme/message profile says so | RAIL_SPECIFIC | RAIL_SPECIFIC | PARTICIPANT_DOCUMENTATION_REQUIRED | PARTICIPANT_DOCUMENTATION_REQUIRED | PARTICIPANT_DOCUMENTATION_REQUIRED |
| submission/interchange | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC |
| validation/acceptance/status | EPC/ISO-qualified | RAIL_SPECIFIC | EPC/ISO-qualified | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC |
| settlement/timing/liquidity/finality | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC |
| delivery/receipt/reconciliation | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC |
| reject/return/recall/cancellation | EPC/ISO-qualified | RAIL_SPECIFIC | EPC/ISO-qualified | RAIL_SPECIFIC | RAIL_SPECIFIC | RAIL_SPECIFIC |

`interchange`, `batch`, `bulk`, `file` and `submission` are terminology collisions, not synonyms. No row authorizes a common Debina rail behaviour; a future profile must cite exact source section and version.
