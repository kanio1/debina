---
name: keycloak-realm-config
description: Use when changing Debina Keycloak realms, clients, roles, seeded users, or realm imports; do not use to invent roles or encode authorization only in the frontend.
---
# Keycloak realm configuration

Before editing, inspect the actual Keycloak version, realm/client export, backend authorities, frontend role-to-screen mapping, planning and ADRs. Derive the current authoritative `role → capability → API → screen → test` matrix; historical role counts are evidence to check, never an authorization rule.

Realm state is `infra/keycloak/realm-export.json`; do not make console-only changes. Keep `sepa-web` server/BFF-only and prevent browser token storage or frontend-only authorization. Do not add or remove a role merely to satisfy a screen or an old iteration document.

Verify applicable client-versus-realm role assignment, issuer, audience, role extraction, least privilege, unauthorized API denial, role-to-screen enforcement, service-account scope and token refresh/revocation. Use the project’s actual compose command and current import checks; do not assert a version from this skill.
