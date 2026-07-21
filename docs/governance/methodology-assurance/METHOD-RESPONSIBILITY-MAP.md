# Method Responsibility Map

| Method | Owns | Must not own | Handoff |
|---|---|---|---|
| Use-Case Foundation | system of interest, external actor goal, basic/alternate flows | payment semantics, modules | Cockburn profile and UC2 slicing |
| Cockburn | readable goal narrative and elaboration | backlog slicing | UC2 |
| Use-Case 2.0 | behavioral slices, tests, realization/increments | technical-layer slices | planning |
| Example Mapping | discovery rules/examples/questions | evidence of human consensus without record | review metadata |
| ISO/EPC/rail | external payment semantics | Debina architecture | source evidence |
| DDD | language, ownership, invariants | actors/goals | architecture review |
| C4 | architecture communication | business rules | dynamic review |
| arc42 | measurable quality scenarios | product flows | ATAM-inspired review |
| ATAM-inspired desk review | risk hypotheses/tradeoffs | stakeholder consensus | ADR candidate when significant |
| ADR | durable significant decisions | external normative truth | architecture model |

Topic-aware precedence is: external payment semantics `law → EPC → rail → ISO → project interpretation`; product behavior `approved use case → product decision → slice → story`; architecture `frozen/accepted ADR → architecture model → realization → implementation`; current behavior evidence `runtime proof → test → code → planning claim`.
