# HANDOFF

## Zadanie

Wave 7 dostarczył źródłowo zamrożony maker–checker prefix gate dla pojedynczych płatności oraz wewnętrzną kolejkę approvera. Wynik: `READY-EXHAUSTED` — osiągnięto wszystkie trzy rzeczywiście READY historie; decyzje/zdolności blokujące resztę są zachowane.

## Zrobione

- EPIC-76 powstał jako brakujący source-derived owner. **76.1** `d87bb44`: V53/V54, RLS/grants, approval axis i ADR-W7-01 (pending payment ma `status=NULL`, nie fałszywy status FSM).
- **76.2** `26d4a9c`: JSON_DIRECT i pain.001 tworzą approval po lineage; `NOT_REQUIRED` wydaje dokładnie jeden istniejący `payment.received`, `PENDING_APPROVAL` nie ma outbox/history/FSM. Maker to stabilny JWT `sub`; niejednoznaczne, step-up i niewspierane selektory fail-closed.
- **76.5** `39621a6`: wewnętrzny `ApprovalQueueReadModel`, `payment_approver`, RLS tenant/branch, cursor `(submitted_at,id)`, uczciwy expired flag. Bez REST/GraphQL.
- Finalne pełne regresje: dwa kolejne `./mvnw -f backend test` **496/0/0**, logi `/tmp/DEBINA-PAYMENT-APPROVAL-MAKER-CHECKER-WAVE-7/final-backend-regression-{1,2}.log`. Planning/capability i skill validator PASS. Nie było push.

## Nadal zablokowane

- EPIC-76 76.3/76.4: brak source-owned `evidence-audit` modułu/portu/tabeli `audit.audit_log`, wymaganych dla atomowego audit approve/reject/expiry.
- 76.6 GraphQL owner, 76.7 Playwright first-three-screens gate, 76.8 real batch aggregate; step-up nadal nie ma pełnego kontraktu.
- D6-01..D6-05 pozostają Class C w `planning/decisions/DEBINA-WAVE-6-CONSOLIDATED-DECISION-PACKET.md`.

## Następny krok

Najwyższy odblokowujący krok: source-derived evidence-audit capability z jawną, source-compatible granicą transakcji; dopiero potem 76.3/76.4. Nie zastępować audytu outboxem, historią statusu ani logami.

## Bezpieczeństwo sesji

- Worktree ma być czysty po commitcie dokumentacji tego handoffu; nie pushowano.
- Maven zmienia `build/generated-spring-modulith/javadoc.json`; został przywrócony.
