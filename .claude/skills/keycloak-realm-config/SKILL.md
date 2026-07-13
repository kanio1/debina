---
name: keycloak-realm-config
description: Use when adding or modifying a Keycloak realm, client, role, or seeded user for local development — realm-export.json edits and the associated import verification.
---
# Keycloak 26.6.4 realm conventions (Iteration 0 scope)

1. Iteration 0 has exactly **4 realm roles**: `operator`, `payment_submitter`, `payment_approver`, `reference_data_admin` — not the full 12. Do not add more roles in Iteration 0 without updating this plan first.
2. Two clients: `sepa-web` (confidential, used only by the Next.js BFF server, never the browser) and `sepa-api` (bearer-only resource server).
3. Realm state lives in `infra/keycloak/realm-export.json`, committed to git — the docker-compose Keycloak service imports it on startup. Never configure the realm by hand in the admin console without exporting the change back to this file.
4. Seed exactly one test user per role, password `dev-only-<role>`, clearly not for anything beyond local dev.
5. After any change: `docker compose up -d keycloak` then `curl -f http://localhost:8080/health/ready` → HTTP 200, and confirm the realm imported via `curl http://localhost:8080/realms/sepa-nexus/.well-known/openid-configuration` → HTTP 200.
