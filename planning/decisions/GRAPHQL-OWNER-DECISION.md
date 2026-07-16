# GraphQL implementation ownership decision

## Status

OPEN — REQUIRES USER/TEAM DECISION

## Problem

No epic in `/planning/` currently owns *building* the first GraphQL schema/resolver layer (`spring-graphql`/`graphql-java` is not a `backend/pom.xml` dependency — confirmed by grep, most recently re-confirmed in the `EPIC-16`/`EPIC-23` planning notes below). Several epics assume a GraphQL layer already exists and only cover a narrower concern:

- `EPIC-16` (`planning/epics/EPIC-16-read-model-graphql-ownership.md`) Story 16.2/16.3 cover *ownership enforcement* of a GraphQL layer (ArchUnit rule that the schema has zero `Mutation` fields, dashboard-projection refresh rule) — they test a schema that would already exist, they do not build one.
- `EPIC-23` (`planning/epics/EPIC-23-frontend-foundation.md`) Story 23.1B covers *codegen* of TypeScript types from a GraphQL SDL file — it needs the SDL to already exist.
- Neither epic's own file claims ownership of writing the first resolver/schema. `EPIC-16`'s own `[PLANNING-DEFECT 2026-07-14]` note says so explicitly: *"w tym repo nie istnieje żadna zależność ani schemat GraphQL"* and marks itself `blocked` rather than claiming the build work.

This gap was first surfaced in the 2026-07-16 backlog-redesign session as an `[OPEN-QUESTION]` inside `EPIC-26` Story 26.4 (see `planning/epics/EPIC-26-iso-message-lineage-core.md` line 51: *"no epic in `/planning/` currently owns building the GraphQL layer itself... Nothing currently specifies who writes the first `spring-graphql` schema/resolver."*) and duplicated as item 5 in `planning/README.md`'s open-questions list. This document is the first dedicated writeup — no decision is made here, only the problem, the options, and what depends on the answer.

## Existing consumers

Stories that assume a GraphQL schema/resolver already exists, i.e. that would become buildable the moment ownership is assigned and the first schema lands:

- `EPIC-26` Story 26.4 — payment-detail lineage panel (`PaymentLineageGraphQLTest`), `depends_on: [Story 26.2, Story 26.3]` (both `done`) — the only remaining blocker is the missing GraphQL layer itself.
- `EPIC-53` Story 53.4 — route-decision explanation read model (`RouteExplanationGraphQLTest`) — additionally gated on `EPIC-53`'s own earlier stories (routing doesn't exist yet), so not immediately unblocked, but will need the same schema/resolver foundation.
- `EPIC-16` Story 16.2 — `GraphQLReadOnlyTest`, an ArchUnit/schema-shape rule with literally nothing to assert against until a schema exists.
- `EPIC-23` Story 23.1B — GraphQL SDL → TypeScript codegen wiring in CI.

Stories that explicitly route work *away* from GraphQL and are therefore *not* blocked by this gap (listed for completeness, not as consumers): `EPIC-68` Story 68.3 and `EPIC-50`'s resend-command story both state their command is REST/gRPC, "never a GraphQL mutation" — consistent with the frozen `[FREEZE]` rule in `EPIC-16`/root `AGENTS.md` that GraphQL is read-only in MVP.

## Existing epics that only assume GraphQL exists

`EPIC-16` (ownership enforcement) and `EPIC-23` Story 23.1B (codegen) — see Problem section. Neither is a candidate to silently absorb the build scope without an explicit decision, because both are written and scoped as enforcement/tooling epics, not as the schema's origin.

## Candidate ownership options

1. **Extend existing `EPIC-16`.** Add a new Story 16.0 (or renumber) that builds the first minimal `spring-graphql` schema/resolver (e.g. payment lineage query only), ahead of today's 16.1–16.3 enforcement stories. Pro: keeps all GraphQL-related ownership work in one file. Con: `EPIC-16`'s `source:` citations (`sepa-nexus-message-flow-and-data-blueprint.md` §8 EPIC-OWN-8; `sepa-nexus-blueprint-ownership-integration.md` §9/§6.6) describe it as an *ownership* epic, not a build epic — adding build scope would need its own source justification, not just convenience.
2. **New story inside `EPIC-23` (Frontend Foundation).** `EPIC-23` already owns `data-testid` conventions, role→screen mapping, and (Story 23.1A/23.1B) REST/GraphQL codegen wiring — a natural adjacent home if GraphQL is treated primarily as a frontend-facing read API. Con: the actual resolver/schema code would live in `backend/`, and `EPIC-23`'s scope as currently sourced is frontend-only; this would be the first backend-owning story in that epic.
3. **New, dedicated read-model/GraphQL epic.** A new `EPIC-XX` (numbered after the current highest, `EPIC-75`) scoped explicitly to "build the first GraphQL read-model layer," with `EPIC-16` continuing to own enforcement once that layer exists, and `EPIC-23`/`EPIC-26`/`EPIC-53` depending on its first story. Pro: matches the pattern already used elsewhere in this backlog (a dedicated epic owns the build, a separate `EPIC-09`/`EPIC-16`-style epic owns cross-cutting enforcement). Con: needs its own `source:` citation — the two blueprint documents already cited by `EPIC-16` describe *that* GraphQL is read-only and owned per-module, not a build sequence or a component ADR for *how* the schema is assembled; that gap itself may need to be closed by the user/team before a new epic file can honestly claim a `source:` field (per this repo's own "nothing is invented" rule).

## Consequences of each option

- Whichever option is chosen, the resulting story must specify: the minimal first query (payment lineage panel is the smallest, already-blocked consumer — `EPIC-26`/26.4), the `spring-graphql`/`graphql-java` dependency addition itself (a `pom.xml` change, currently absent), and how it interacts with the frozen `[FREEZE]` rule (GraphQL read-only in MVP, zero mutations) — i.e. `EPIC-16` Story 16.2's ArchUnit rule becomes the acceptance gate for whatever story builds the schema, regardless of which epic that story lives in.
- Options 1 and 2 avoid a new epic number but stretch an existing epic's sourced scope; Option 3 stays cleanest structurally but is blocked on finding (or the user supplying) a source document for the build sequence itself, per the "don't invent architecture" rule this repo enforces throughout `AGENTS.md`.

## Recommended decision criteria

Not recommending an option — this is the user/team's call, not an architectural decision this session is authorized to make (`AGENTS.md`: "Nigdy nie wymyślaj nowej architektury"). Suggested criteria to weigh, without picking among them:

- Does a source document already describe *how* the first GraphQL schema should be assembled (resolver-per-module pattern, schema-stitching, a single monolithic schema)? If yes, whichever epic that document maps to is the natural owner.
- Is the intended first-and-only near-term GraphQL surface exclusively the payment-lineage panel (`EPIC-26`/26.4)? If the near-term scope really is that narrow, Option 1 or 2 (extend an existing epic) may be proportionate. If routing (`EPIC-53`) and later `reporting`-module dashboards (`EPIC-16` Story 16.3) are all near-term, Option 3 (dedicated epic) avoids overloading a single existing epic's scope.
- Who is expected to write the code — is it inherently a backend concern (schema/resolver in `backend/`, argues for a backend-owning epic near `EPIC-16`) or should it be planned as part of the frontend foundation work already tracking codegen (`EPIC-23`)?

## Stories blocked by this decision

- `EPIC-26` Story 26.4 (`depends_on: [Story 26.2, Story 26.3]`, both `done` — the GraphQL-layer gap is now its only real blocker)
- `EPIC-16` Story 16.2, Story 16.3 (nothing to enforce/test against until a schema exists)
- `EPIC-23` Story 23.1B (nothing to generate TypeScript types from until an SDL exists)
- `EPIC-53` Story 53.4 (also gated on earlier `EPIC-53`/routing stories, but shares the same underlying gap)

## Explicit non-decision

This document records the problem, the consumers, and the options. It does **not** select an owner, does **not** create a new epic file, and does **not** modify `EPIC-16`, `EPIC-23`, `EPIC-26`, or `EPIC-53`'s `depends_on` beyond linking to this decision gate in the capability graph. Per `AGENTS.md`'s "record open questions instead of resolving them yourself" rule, resolving this requires an explicit user/team decision, which should be recorded here (status changed from `OPEN` to `RESOLVED`, with the chosen option and date) before any story above is unblocked on this specific gap.
