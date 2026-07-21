# Stakeholders and Personas

Keycloak roles are technical authorization constructs, not this business model. A persona can need several permissions and a permission can support several personas.

| Category | Actor/persona | Goal | Boundary |
|---|---|---|---|
| Business stakeholder | participant PSP/bank | submit, receive and investigate payment business | external organization; simulated unless evidenced |
| Human system user | payment submitter / approver | create payment; approve or reject when policy requires | payment lifecycle |
| Human system user | operations analyst / case investigator | investigate lifecycle, status, exception and correlation | read models; case is future |
| Human system user | settlement and liquidity operator | inspect settlement attempts/cycles and liquidity evidence | settlement/ledger |
| Human system user | reconciliation analyst / auditor | compare evidence and inspect immutable audit trail | reconciliation future / evidence-audit |
| Administrative role | security, reference-data and platform administrator | manage identity, configuration or runtime | not automatically a business persona |
| External technical system | customer channel, CSM, external participant | exchange business messages/receipts | simulated/public-adapter boundary |
| External technical system | Keycloak | authenticate and issue identity assertions | identity provider |
| Service account | dispatcher, relay, backend service identity | constrained machine operation | database/broker grant boundary |

Central-bank and settlement-system abstractions are external systems, never Debina users or asserted live integrations.
