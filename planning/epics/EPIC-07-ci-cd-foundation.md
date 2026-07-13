---
status: not-started
depends_on: [EPIC-03-spring-modulith-backend-skeleton, EPIC-06-react-shadcn-frontend-skeleton]
source: "sepa-nexus-iteration-0-foundation-plan.md, EPIC 7 (Story 7.1-7.3), lines 544-564"
---

# EPIC-07 — CI/CD Foundation

Te same komendy weryfikacyjne z Epików 1-6 uruchamiane bez nadzoru, odtwarzalne lokalnie przez `nektos/act` zanim zacznie się polegać na runnerach GitHub.

## Story 7.1 — Workflow CI backendu

status: not-started
depends_on: [EPIC-03-spring-modulith-backend-skeleton/Story 3.1]

Opis: `.github/workflows/backend.yml`, `ModularityTest` jako bramka blokująca — źródło: linie 548-553.

Kryterium ukończenia: job przechodzi lokalnie przez `act`, a celowe naruszenie granic modułu faktycznie wywala build.

Taski:
- [ ] **`.github/workflows/backend.yml`**: checkout → JDK 25 → `./mvnw -f backend test` (Testcontainers potrzebuje Docker-in-Docker lub bloku `services:` Postgres+Kafka; preferuj natywne wsparcie Testcontainers dla Docker socket na hostowanym runnerze GitHub).
      `verify: act -W .github/workflows/backend.yml -j test` (lokalnie, przez nektos/act) → job się udaje.
- [ ] **Wywal build na teście architektury Modulith** (`ModularityTest` ze Story 3.1) — to jedyny test w całym planie, który jeśli pominięty, cicho unieważnia dyscyplinę granic modułów, na której opiera się wszystko inne.
      `verify: celowo dodaj zabroniony import cross-module, uruchom workflow, potwierdź że pada; potem cofnij.`

## Story 7.2 — Workflow CI frontendu

status: not-started
depends_on: [EPIC-06-react-shadcn-frontend-skeleton/Story 6.4]

Opis: `.github/workflows/frontend.yml`, celowo bez joba Playwright — źródło: linie 555-558.

Kryterium ukończenia: job build przechodzi lokalnie przez `act`.

Taski:
- [ ] **`.github/workflows/frontend.yml`**: checkout → Node 20 → `npm ci` → `npm run lint` → `npm run build`. Brak joba Playwright w tym workflow — celowo nieobecny do Iteracji 1.
      `verify: act -W .github/workflows/frontend.yml -j build` → job się udaje.

## Story 7.3 — Lokalna parytet CI przez `nektos/act`

status: not-started
depends_on: [Story 7.1, Story 7.2]

Opis: `.actrc` przypinający rodzinę obrazów runnera — źródło: linie 560-563.

Kryterium ukończenia: `act -l` listuje oba workflowy bez błędów konfiguracji.

Taski:
- [ ] **`.actrc`** przypinający tę samą rodzinę obrazów runnera co GitHub Actions, żeby wyniki `act` były wystarczająco zbieżne z hostowanym CI, by im ufać lokalnie.
      `verify: act -l` → listuje joby obu workflowów bez błędów konfiguracji.
