# Source Authority Matrix

## Default order

For a claim, rule, or model, use the highest applicable authority:

1. EU law/regulation
2. EPC Scheme Rulebook and Implementation Guidelines
3. rail-specific official CSM specification
4. ISO 20022 Business Model/MDR/MUG/XSD
5. accepted project ADR
6. project blueprint
7. use case
8. epic/story
9. implementation
10. assumption

This is not a universal ranking. Authority is topic-specific: ISO defines message semantics and structure; EPC defines scheme behaviour; CSM documentation defines rail-specific processing; a project ADR defines Debina implementation choices. A lower source cannot redefine a higher source. Where a higher source is unavailable, record the gap with `[OPEN-QUESTION]` or `[ASSUMPTION]`; do not fabricate participant-only behaviour.

## Evidence tags

| Tag | Meaning |
|---|---|
| `[EU-LAW]` | Official EU legal text |
| `[EPC-SCT]` / `[EPC-SCT-INST]` | EPC SCT / SCT Inst source |
| `[ISO20022]` | ISO 20022 model, MDR, MUG, or XSD |
| `[TIPS]`, `[STEP2]`, `[RT1]`, `[STET]` | Official rail-specific source |
| `[PROJECT-ADR]` | Accepted or Frozen Debina ADR |
| `[PROJECT-SIMULATION]` | Synthetic laboratory choice |
| `[ASSUMPTION]` | Explicit, revisitable gap-filler |
| `[OPEN-QUESTION]` | Decision or evidence still required |

Use a tag and a registry key near every standards-backed rule. Links belong in [SOURCE-REGISTRY.yaml](SOURCE-REGISTRY.yaml); copyrighted rulebooks and participant documents are never copied into this repository.
