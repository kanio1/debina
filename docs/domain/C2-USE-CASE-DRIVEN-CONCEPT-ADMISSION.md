# C2 Use-Case-Driven Concept Admission

The YAML record is authoritative. C2 admits no new aggregate. `PaymentOrder` remains sufficient for the implemented lifecycle; `PaymentInstruction` is a business document, not an aggregate; `PaymentGroup` is a rail-qualified grouping construct; `PaymentBatch` is deferred; `ClearingSubmission`, Interchange and InterchangeFile are rail-specific or insufficiently evidenced; `TransportFile` is an egress technical artifact. Any future aggregate proposal is an `ADR-REVIEW-CANDIDATE` and must satisfy the aggregate-admission record.
