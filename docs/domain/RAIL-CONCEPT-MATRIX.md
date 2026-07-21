# Rail Concept Matrix

Status values: `SUPPORTED`, `NOT-APPLICABLE`, `RAIL-SPECIFIC`, `PROJECT-SIMULATION`, `UNKNOWN`, `PARTICIPANT-DOCUMENTATION-REQUIRED`, `CONFLICTING-SOURCES`. Sources: `epc-sct`, `epc-sct-inst`, `step2-public`, `rt1-public`, `tips-public`, `stet-public` in the registry. Public landing pages are insufficient for participant-only details.

| Aspect | generic SCT | STEP2 SCT | generic SCT Inst | RT1 | TIPS | STET |
|---|---|---|---|---|---|---|
| primary processing unit | SUPPORTED: credit transfer | RAIL-SPECIFIC | SUPPORTED: instant credit transfer | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC |
| single vs multiple transactions | RAIL-SPECIFIC | RAIL-SPECIFIC | SUPPORTED: transaction intent | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC |
| group/batch/bulk/file | SUPPORTED only where scheme/message profile says so | RAIL-SPECIFIC | RAIL-SPECIFIC | PARTICIPANT-DOCUMENTATION-REQUIRED | PARTICIPANT-DOCUMENTATION-REQUIRED | PARTICIPANT-DOCUMENTATION-REQUIRED |
| submission/interchange | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC |
| validation/acceptance/status | EPC/ISO-qualified | RAIL-SPECIFIC | EPC/ISO-qualified | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC |
| settlement/timing/liquidity/finality | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC |
| delivery/receipt/reconciliation | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC |
| reject/return/recall/cancellation | EPC/ISO-qualified | RAIL-SPECIFIC | EPC/ISO-qualified | RAIL-SPECIFIC | RAIL-SPECIFIC | RAIL-SPECIFIC |

`interchange`, `batch`, `bulk`, `file` and `submission` are terminology collisions, not synonyms. No row authorizes a common Debina rail behaviour; a future profile must cite exact source section and version.
