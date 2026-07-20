# Message lineage identifiers

Maintain distinct columns/facts for message identity (`BizMsgIdr`, `MsgId`), instruction identity (`InstrId`), payment/end-to-end identity (`EndToEndId`), transaction identity (`TxId`), and applicable universal tracking (`UETR`). Their exact presence, cardinality, preservation and correlation use must come from the selected message version and source; absent identifiers are not invented.

Record the relationship rather than replacing an identifier: raw inbound/outbound evidence → source ISO message → parsed/validated message fact → canonical/business payment → derived outbound message or response. Preserve original values and record any derived value and derivation source explicitly.

Classify an apparent repeat as one of:

- duplicate: same accepted message/idempotency identity and same protected payload;
- replay: a previously processed message delivered again, with a deterministic no-second-effect outcome;
- conflict: same protected identity with materially different content, fail closed/escalate per source;
- business resubmission: a distinct business instruction only where the source and business policy say so.

Correlation never authorizes a best guess. Preserve `MATCHED`, `AMBIGUOUS`, `ORPHANED`, or source-backed equivalent outcomes and keep the `iso-adapter` from deciding business state.
