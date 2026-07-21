# Architecture Method

Debina evolves one Spring Modulith modular monolith. Use the C4 requirements, DDD context map, module catalog, quality utility tree, quality scenarios, and ATAM-lite review before admitting material architecture change. An admission needs a source-backed use-case or quality scenario, owner/data boundary, dependency direction, security/RLS/grant effect, operational evidence, and reversibility. A proposed module/bounded context must show why an existing module plus public port cannot own the responsibility; it needs a classification, catalog entry, allowed dependencies, one-writer schema decision (or explicit no-schema rationale), tests, and ADR where a frozen boundary is affected.

Do not merge/split existing production modules through this method alone.
