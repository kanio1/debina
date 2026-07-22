# Dagger and Podman troubleshooting

The recorded host has rootless Podman 5.8.4. Dagger's official Podman documentation requires rootful execution and warns of an Engine PID limit that is often 2048. Phase D did not alter this setting, start a custom runner or add Docker compatibility shims.

After the owner satisfies the prerequisite, run the minimal Dagger engine probe first. Record the actual runtime mode, runner host, Engine PID limit and exact failure before changing repository code. If the probe fails, return `ENVIRONMENT-BLOCKED-CHECKPOINT` with the command, full non-secret error, minimal user-owned host change, security implication and retry command.
