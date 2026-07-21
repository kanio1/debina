#!/usr/bin/env bash
set -euo pipefail

# Consistent local Keycloak backup: stop only the Keycloak writer, then capture its
# dedicated PostgreSQL database and the supported offline realm export. It never
# touches the payment PostgreSQL service or a broad Podman volume target.
if [[ $# -ne 1 ]]; then
  echo "usage: $0 <empty-or-new-backup-directory>" >&2
  exit 64
fi

backup_dir=$1
if [[ -e "$backup_dir" && -n "$(find "$backup_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  echo "backup directory must be empty: $backup_dir" >&2
  exit 65
fi

compose_file=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/docker-compose.yml
compose=(podman compose -f "$compose_file")
container_id_for() {
  podman ps -aq --filter 'label=com.docker.compose.project=infra' \
    --filter "label=com.docker.compose.service=$1" | head -n 1
}
umask 077
mkdir -p "$backup_dir"
realm_dir="$backup_dir/realm"
mkdir -p "$realm_dir"

keycloak_container=$(container_id_for keycloak)
was_running=$(podman inspect -f '{{.State.Running}}' "$keycloak_container" 2>/dev/null || true)
restart_keycloak() {
  if [[ "$was_running" == "true" ]]; then
    "${compose[@]}" start keycloak >/dev/null
  fi
}
trap restart_keycloak EXIT

"${compose[@]}" stop keycloak >/dev/null
"${compose[@]}" exec -T keycloak-postgres pg_dump -U keycloak --format=custom --file=/tmp/keycloak.dump keycloak
keycloak_db_container=$(container_id_for keycloak-postgres)
podman cp "$keycloak_db_container:/tmp/keycloak.dump" "$backup_dir/keycloak.dump"
"${compose[@]}" exec -T keycloak-postgres rm -f /tmp/keycloak.dump

export_container=$("${compose[@]}" run -d --no-deps -T keycloak export --dir /tmp/keycloak-realm-export --realm sepa-nexus)
podman wait "$export_container" >/dev/null
podman cp "$export_container:/tmp/keycloak-realm-export/." "$realm_dir"
podman rm "$export_container" >/dev/null

sha256sum "$backup_dir/keycloak.dump" "$realm_dir"/* > "$backup_dir/SHA256SUMS"
