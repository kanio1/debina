---
status: done
depends_on: [EPIC-00-repository-agent-foundation]
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 2 (Story 2.1-2.2), lines 281-300"
---

# EPIC-02 — Keycloak 26.6.4 Realm (4 role, zakres Iteracji 0)

Realny dostawca tożsamości OIDC, podłączony end-to-end — nie zaślepiony — bo auth jest dokładnie tym, co drogo dorabiać później. Zakres Iteracji 0 to celowo tylko 4 role (nie 11-12 z docelowego modelu ról — patrz EPIC-74 dla pełnego zakresu bezpieczeństwa).

## Story 2.1 — Realm-as-code

status: done
depends_on: [EPIC-00-repository-agent-foundation/Story 0.3]

Opis: `infra/keycloak/realm-export.json` z realmem `sepa-nexus`, czterema rolami, dwoma klientami, czterema użytkownikami testowymi — źródło: linie 287-294.

Kryterium ukończenia: realm importuje się, wszyscy czterej użytkownicy testowi potrafią uzyskać token.

Taski:
- [x] **Autoryzuj `infra/keycloak/realm-export.json`** z realmem `sepa-nexus`, czterema rolami realmu (`operator`, `payment_submitter`, `payment_approver`, `reference_data_admin`), mapperami claimów `tenant_id`/`branch_id` (atrybuty użytkownika → JWT, te same claimy zasilające GUC z EPIC-01).
      `verify: podman compose -f infra/docker-compose.yml up -d keycloak && curl -fsS http://localhost:8080/realms/sepa-nexus/.well-known/openid-configuration` → PASS (2026-07-14).
- [x] **Dodaj dwóch klientów**: `sepa-web` (confidential, `standardFlowEnabled: true`, redirect `http://localhost:3000/*`, tylko serwer BFF) i `sepa-api` (bearer-only resource server, `publicClient: false`).
      `verify: curl -fsS http://localhost:8080/realms/sepa-nexus/.well-known/openid-configuration | jq '.token_endpoint'; podman compose -f infra/docker-compose.yml exec -T keycloak kcadm.sh get clients -r sepa-nexus` → PASS (2026-07-14).
- [x] **Zasiej czterech użytkowników testowych**, po jednym na rolę, hasło `dev-only-<role>`.
      `verify: for u in operator submitter approver refdata; do curl -fsS -X POST http://localhost:8080/realms/sepa-nexus/protocol/openid-connect/token -d 'client_id=sepa-web' -d 'client_secret=dev-only-sepa-web-secret' -d 'grant_type=password' -d "username=$u" -d "password=dev-only-$u" | jq -e '.access_token' > /dev/null; done` → PASS (2026-07-14): wszyscy czterej się udają. [SECURITY-NOTE] Password grant jest wyłącznie lokalną kontrolą seedów Iteracji 0, a nie docelowym przepływem BFF.

## Story 2.2 — Weryfikacja kształtu tokena

status: done
depends_on: [Story 2.1]

Opis: JWT niesie dokładnie potrzebne claimy, bez nadmiarowych danych wrażliwych — źródło: linie 296-299.

Kryterium ukończenia: kształt tokena zweryfikowany ręcznie/skryptem.

Taski:
- [x] **Potwierdź wymagane claimy JWT**: `tenant_id`, `branch_id` (nullable), `realm_access.roles`, `sub`, `sid`, bez nadmiarowych danych wrażliwych.
      `verify: TOKEN="$(curl -fsS -X POST http://localhost:8080/realms/sepa-nexus/protocol/openid-connect/token -d 'client_id=sepa-web' -d 'client_secret=dev-only-sepa-web-secret' -d 'grant_type=password' -d 'username=operator' -d 'password=dev-only-operator' | jq -r .access_token)"; printf '%s' "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d | jq -e '.tenant_id and .branch_id and .realm_access.roles and .sub and .sid and (has("address") | not) and (has("phone_number") | not) and (has("groups") | not)'` → PASS (2026-07-14).

## Defekty planningu

- `[PLANNING-DEFECT]` Bind mount realm export na Fedora/SELinux wymaga etykiety `:Z`; zweryfikowano import Keycloak po dodaniu jej wyłącznie do tego mountu.
- `[PLANNING-DEFECT]` Standardowy JWT OIDC zawiera techniczne claimy poza pięcioma wymaganymi. Weryfikacja sprawdza obecność wymaganego kontraktu i brak niezamówionych danych wrażliwych zamiast błędnego równego licznika top-level claims.

## Otwarte pytania

- `[OPEN-QUESTION]` Dokumentacja bezpieczeństwa (oba blueprinty Keycloak) nigdzie nie wspomina "4 role" dla Iteracji 0 — jedyna liczba tam podana to 11 (starszy dok) / 12 (nowszy dok, po dodaniu `payment_approver`). "4 role" pochodzi wyłącznie z `sepa-nexus-iteration-0-foundation-plan.md` (ten epik) i z dokumentu wizji Playwright (§9.9: "3-4 role Keycloak, nie 11 od razu"). Nie ma sprzeczności blokującej — Iteracja 0 świadomie zawęża zakres — ale warto to mieć odnotowane przy planowaniu EPIC-74 (pełny model ról).
