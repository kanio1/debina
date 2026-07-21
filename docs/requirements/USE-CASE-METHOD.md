# Use-Case Engineering Method

Debina uses Use-Case Foundation as the universal base: a use case collects all basic, challenged and failure flows by which an **external** primary actor achieves a goal using the declared system of interest. Cockburn supplies readable narrative profiles; Use-Case 2.0 supplies behavioral slices, tests and realization/increment links. See the methodology assurance responsibility map for method boundaries.

## Adaptive elaboration

Choose `OUTLINE` for discovery/source-blocked work, `ESSENTIAL` for normal delivery preparation, and `FULLY_DRESSED` only for money/finality, security, concurrency/recovery, interoperability/rail uncertainty or significant tradeoffs. A profile states required detail; it does not assert review quality.

## Required structure

Every ENFORCED record declares system of interest/boundary, external actor/type, actor goal/goal level, profile, discovery status, material-question state, methodology, source evidence status and architecture-evaluation classification. Flows use stable `BF-*` and `AF-*`/`CF-*`/`FF-*` IDs. An alternate identifies entry flow, condition, steps, rejoin/termination and guarantee. Internal modules appear only in architecture realization.

Only `BEHAVIORAL` or `RISK_REDUCTION` items are UC2 slices: they select parent flows, have an observable outcome/start/end, link to test cases or an explicit preparation gap, and connect to realization. UI adapters, test automation and future batch candidates are realization/test/candidate records, not slices.

Example Mapping records rules/examples/questions. Collaboration is `AI_DRAFT`/`NOT_REVIEWED` until durable review evidence proves otherwise; `out_of_scope` is a Debina extension. Material open questions block READY. Stories implement/prove slices but never redefine higher-authority behavior.
