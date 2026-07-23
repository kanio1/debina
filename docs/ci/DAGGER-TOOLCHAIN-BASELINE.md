# Dagger toolchain baseline

## D0 gate reverified 2026-07-22

| Item | Observed | Phase D decision |
|---|---|---|
| Dagger CLI | `v0.21.4` | pinned; do not upgrade during Phase D |
| Dagger Engine | `v0.21.4` | reachable through the selected rootful Podman API; module declares the same version |
| Go host | `go1.26.5-X:nodwarf5 linux/amd64` | compiles the CLI-generated Go module |
| Podman | `5.8.4`, linux/amd64, rootful, cgroup v2 | selected OCI runtime |
| Host | Fedora 44, kernel `7.1.4-200.fc44.x86_64`, amd64 | recorded execution host |
| Rootful API | `unix:///run/podman/podman.sock` | accessible without `sudo`; `rootless=false` |
| Socket protection | directory `root:podman-rootful 0750`; socket `root:podman-rootful 0660` | user-owned authorization accepted for this trusted local session; no host setting changed by Phase D |
| Dagger Engine | privileged, effective PID limit `16384` | approved bounded limit; gate passes |

`CONTAINER_HOST` and `DOCKER_HOST` select the rootful socket. Their values are endpoint metadata, not credentials; no secret-valued environment variable is recorded.

## Deterministic probe

The selected runtime passed both `podman --remote --url unix:///run/podman/podman.sock ps` and this no-repository-content Dagger probe:

```bash
dagger core container from --address alpine:3.22 \
  with-exec --args echo --args dagger-d0-probe stdout
```

Output: `dagger-d0-probe`.

## Result

`D0-PASSED`. The prior `ENVIRONMENT-BLOCKED-CHECKPOINT` is superseded. The rootful Podman API is security-sensitive and intentionally remains a workstation-owner decision; Phase D made no group, socket, systemd, SELinux, containers.conf or PID-limit change.

## Architecture-review version decision — 2026-07-23

The latest patch in the pinned line is `v0.21.7`. It passed `fast`, frozen image
lookup and the `WithServiceBinding` DNS regression when invoked through
`dagger --x-release v0.21.7`. Releases 0.21.5–0.21.7 include relevant cache,
filtered-directory, garbage-collection and CNI fixes.

Decision: `DEFER`. Applying the official `dagger develop` upgrade changed only
`engineVersion`, after which the installed v0.21.4 CLI correctly refused every
ordinary module call. The controlled change was reverted by patch. Do not
require a newer Engine without atomically installing the matching CLI. A future
upgrade must regenerate bindings and repeat frozen-lock, workspace invalidation,
service DNS, cache, assurance and full Phase D proofs. See
[the review](DAGGER-ARCHITECTURE-REVIEW-2026-07-23.md).
