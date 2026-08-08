# Bootstrap probe removal evidence

- **File:** `tools/agent_policy/_bootstrap_probe.txt`
- **Created by:** earlier harness bootstrap shell probe during BLOCKED maintenance (`DEBINA_AGENT_WRAPPER=1 python3 -c` write test)
- **Purpose:** confirmed shell could write to `tools/agent_policy/` when hooks were fail-closed
- **Removal reason:** temporary artifact; not part of harness policy
- **Removed:** during FIX-CURSOR-SPEC-KIT-SPECIFICATION-SCOPE completion after import restore and synthetic test pass
