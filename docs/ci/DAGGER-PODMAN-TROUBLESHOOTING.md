# Dagger and Podman troubleshooting

The recorded host has rootful Podman 5.8.4. Dagger's official Podman documentation requires rootful execution and warns of an Engine PID limit that is often 2048; this Phase D host has the pre-existing privileged Engine limit of 16384. Phase D did not alter that setting, start a custom runner or add Docker compatibility shims.

After the owner satisfies the prerequisite, run the minimal Dagger engine probe first. Record the actual runtime mode, runner host, Engine PID limit and exact failure before changing repository code. If the probe fails, return `ENVIRONMENT-BLOCKED-CHECKPOINT` with the command, full non-secret error, minimal user-owned host change, security implication and retry command.

For Testcontainers, Dagger v0.21.4 can accept a host Unix socket only as an explicit typed `Socket` function argument. Use `dagger call testcontainers-regression --runtime-socket=/run/podman/podman.sock`; it is not valid to make that resource silently optional in `dagger check integration`. The typed socket may be mounted only at `/var/run/docker.sock` inside the dedicated Maven/Testcontainers container. If created containers are reachable only through Podman’s host address, first prove `host.containers.internal` from a socket-free diagnostic container before setting `TESTCONTAINERS_HOST_OVERRIDE`.
