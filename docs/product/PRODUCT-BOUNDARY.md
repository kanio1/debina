# Product Boundary

Authoritative external facts belong to `docs/standards/SOURCE-REGISTRY.yaml`; concepts belong to the domain catalogue and implementation state belongs to the module catalogue.

| In product scope | Simulated external boundary | Prohibited claim |
|---|---|---|
| SEPA credit-transfer research flows, ISO message handling, payment/settlement evidence | customer channel, PSP/participant, STEP2, RT1, TIPS, STET and counterparties | live participation, settlement-system integration or scheme certification |
| tenant-aware command/read paths, audit/evidence and operational research | Keycloak identity provider and simulated CSM responses through public paths | bank operation, regulatory compliance or production readiness |
| local developer/Codex verification architecture | provider-neutral lab runtime | selected remote CI or deployment provider |

`JSON_DIRECT` is a declared Debina synthetic input (`[PROJECT-ADR] ADR-N7`), not an ISO message. A public standard source supports only the external fact it actually states; rail-specific operational behaviour is never generalized to another rail.
