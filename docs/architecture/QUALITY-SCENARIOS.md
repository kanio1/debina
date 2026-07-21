# Quality Scenarios

| ID | Attribute | Source/stimulus/environment | Affected artifact | Measurable response | Processes / contexts | Evidence / gap | Priority |
|---|---|---|---|---|---|---|---|
| QS-SEC-01 | tenant isolation | [PROJECT-ADR] UUID probe in authenticated production-like test | RLS/source query ports | returns no object-existence information for foreign tenant/branch | BP-01/BP-10; payment, ISO, GraphQL | targeted runtime evidence; expand matrix | High |
| QS-SEC-02 | authorization | unauthorized signature/key action | signature/security | denied before key material or state is exposed | BP-01/BP-09; signature | structural/runtime evidence partial | High |
| QS-INT-01 | idempotency | same submit command replay | ingress/payment | exactly one durable business effect; changed payload conflicts | BP-01; ingress/payment | implemented evidence | High |
| QS-INT-02 | transactional integrity | reserve/post/release and finality command failure | settlement/ledger/payment | rollback leaves no partial money effect; one terminal reservation state | BP-05; settlement/ledger | Testcontainers proof; rail trigger gap | High |
| QS-REL-01 | recovery | broker unavailable after owned DB commit | per-schema outbox | durable event is later relayable; no silent loss | BP-01/BP-07; payment/ISO | evidence exists in slices; cross-module matrix gap | High |
| QS-REL-02 | failure isolation | egress/simulation failure | egress/simulation | business finality is unchanged; transport failure is separately recorded | BP-09; egress | delivery receipt model gap | Medium |
| QS-TRC-01 | traceability | operator follows payment identifier | payment/ISO/audit | payment → ISO message → correlation/evidence/audit can be located through preserved IDs | BP-07/BP-10 | GraphQL evidence partial; unified investigation gap | High |
| QS-MOD-01 | modifiability | add supported ISO version or rail adapter | ISO/routing/settlement | no PaymentOrder aggregate rewrite; adapter/profile change has contract test | BP-01/BP-04; ISO/routing | PROJECT-TARGET; use-case needed | Medium |
| QS-OPS-01 | local verification | clean workstation invokes fast check | future Dagger | `dagger check fast` target completes within PROJECT-TARGET set in Phase D | cross-cutting; local verification | planned only | Medium |
| QS-DEP-01 | deployment repeatability | clean workstation integration run | Podman/Testcontainers | documented local command provisions declared dependencies reproducibly | cross-cutting | partial current commands; Phase D gap | Medium |
