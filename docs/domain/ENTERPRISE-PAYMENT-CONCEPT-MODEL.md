# Enterprise Payment Concept Model

Three semantic layers are distinct: (1) business intent and instruction, (2) message/file/envelope/exchange, and (3) clearing and settlement. Classifications are candidates, not implementation mandates. Sources use the authority matrix; `rail-specific` means only create it when an applicable rail source supports it.

| Concept | Definition / authority / rail | DDD candidate, identity, lifecycle and invariant | Kind |
|---|---|---|---|
| PaymentOrder | Payer's business intent `[EPC-SCT]` | Aggregate candidate; order id; initiated→completed/cancelled; intent must be attributable | core |
| PaymentInstruction | Executable instruction derived from an order `[ISO20022]` | Entity/value candidate; instruction id; created→submitted; preserves instructed parties/amount | core |
| PaymentGroup | Grouping of instructions where source permits `[ISO20022]` | Entity/value candidate; group id; assembled→released; membership immutable after release | core |
| PaymentBatch | Operational collection for processing `[PROJECT-SIMULATION]` unless rail-defined | Aggregate only if independent commands/invariants pass; batch id; open→closed | rail-specific |
| BusinessMessage | ISO business payload `[ISO20022]` | Entity/evidence; message id; received→validated; payload lineage immutable | core |
| BusinessApplicationHeader | ISO AppHdr `[ISO20022]` | Value object/evidence; header/message identifier; immutable with message | core |
| BusinessMessageEnvelope | Technical wrapper around a business message `[ISO20022]` | Entity/value; envelope id; received→unwrapped; preserves containment | technical |
| BusinessFileEnvelope | Wrapper/file-level metadata `[ISO20022]` | Entity/evidence; file/envelope id; received→processed; content integrity | technical |
| TransportFile | Physical delivery artifact `[PROJECT-SIMULATION]` or rail source | Evidence/entity; transport id; created→delivered/failed; transport ≠ finality | rail-specific |
| InterchangeFile | Rail interchange file `[TIPS]/[STEP2]/[RT1]/[STET]` when sourced | Aggregate candidate only with independent recovery/commands; interchange id | rail-specific |
| ClearingSubmission | Submission to clearing rail | Aggregate candidate; submission id; prepared→accepted/rejected; replay identity | rail-specific |
| ClearingBatch | Rail clearing grouping | Entity/aggregate candidate; batch id; assembled→closed; membership rule source-backed | rail-specific |
| Bulk | Rail-specific bulk construct | Entity/value candidate; bulk id; source-defined lifecycle | rail-specific |
| SettlementInstruction | Instruction to settle | Entity/command record; instruction id; requested→settled/rejected; never equates delivery with finality | rail-specific |
| SettlementCycle | Deferred settlement window `[PROJECT-ADR]` ADR-N13 | Aggregate candidate; cycle id; OPEN→…→SETTLED; no backward state | core |
| SettlementPosition | Net/gross position `[PROJECT-ADR]` / rail source | Entity/value candidate; position id; calculated→settled; attributable immutable calculation | core |

Before implementation, replace generic authority tags with the exact registry key and version, complete the aggregate-admission record, and specify owning module/data boundary.
