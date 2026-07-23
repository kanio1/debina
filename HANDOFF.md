# HANDOFF

## Zadanie

Realizacja programu `docs/ci/DAGGER-FINAL-ARCHITECTURE-CONTRACT.md` w ośmiu
lokalnych falach. Celem jest jeden automatyczny `acceptance`, trwała
klasyfikacja testów, finalne grafy integration/full-local, pełny runtime proof
i czysty worktree bez `git push`.

## Zrobione

- Wave 0 odtworzyła stan, zapisała finalny kontrakt i zakończyła się lokalnym
  commitem `18818e1 docs(ci): record final dagger architecture contract`.
- Wave 1 ustanowiła finalne nazwy/topologię i zakończyła się commitem
  `159a736 refactor(ci): establish final acceptance topology`.
- Wave 2 wprowadziła rozłączne tagi 39 klas `fast` / 99 klas
  `testcontainers`, equivalence 146+397=543 i zakończyła się commitem
  `b654a10 test(ci): classify backend suites by durable tags`.
- Wave 3 zastąpiła integration dokładnie pięcioma leaves:
  `backend-integration`, `frontend-production-build`, `database-contract`,
  `database-upgrade`, `kafka-contract`. AST regression blokuje zmianę tej
  membership.
- `backend-integration` jest jedynym dzieckiem acceptance wykonującym 146
  backendowych testów `fast`; usunięto wcześniejsze logiczne powtórzenie z
  top-level `fast`.
- `database-contract` tworzy dokładnie jedną świeżą instancję PostgreSQL i
  wymusza markerami kolejność readiness → migrate+validate → `sepa_app`
  credential → RLS/grants. AST regression wymaga jednego wywołania
  `postgresService("database-contract")`.
- `database-upgrade` jest niezależnym v54→current proof na osobnej instancji.
  `kafka-contract` zachowuje produce/consume non-production probe.
- Focused runtime `dagger call integration --lock=frozen --progress=plain`
  zakończył się kodem 0 w 15.9s: backend 146/0/0/0,
  `DATABASE-CONTRACT-VERIFIED`, `DATABASE-UPGRADE-CONTRACT-VERIFIED`,
  `KAFKA-CONTRACT-VERIFIED`. Trace zawiera dokładnie dwa database instance
  labels: `database-contract` i `upgrade`.
- Chroniony `build/generated-spring-modulith/javadoc.json` został po Maven
  odtworzony do HEAD i ma oczekiwany SHA-256
  `47b1b89f63804b4062cd6abe9242a7d56b2212636de95a64784d53723c03e054`.

## Utknęliśmy na

Nic nie blokuje. Wave 3 jest gotowa do lokalnego commita. Zachowane wcześniejsze
zmiany dokumentacyjne/planning nadal pozostają poza tym selektywnym commitem.

## Plan na następny krok

Zacommitować Wave 3, następnie przejść do Wave 4. Udowodnić build-time versus
runtime frontend env, wyodrębnić bezpiecznie wspólny production build albo
zachować dwa vertices z maksymalnie wspólnymi dependency/source layers.
Potwierdzić integration, smoke oraz cache bez zmiany runtime behavior.

## Pułapki, których nie wolno powtórzyć

- Nie dodawać backend `fast` z powrotem do top-level `Fast`; jego właścicielem
  w acceptance jest `backend-integration`.
- Nie uruchamiać unfiltered `BackendRegressionAll` jako dziecka `FullLocal`;
  duplikowałoby 146 testów `fast`.
- Nie rozbijać `database-contract` na niezależne fresh services; upgrade ma
  pozostać jedyną drugą instancją.
- Nie dodawać ambient host socketu, nested Dagger CLI, remote CI ani `git push`.
- Nie zmieniać chronionego `build/generated-spring-modulith/javadoc.json`.
