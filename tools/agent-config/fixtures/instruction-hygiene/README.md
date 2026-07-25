# Instruction-hygiene fixtures

Deterministic cases for `validate-agent-instruction-hygiene.py --self-test`
are built in an ephemeral temporary directory. This folder documents the
covered scenarios; it is not mutated by the self-test.

Covered cases: valid MDC, missing frontmatter, empty description, overbroad
specialist rule, valid universal rule, hidden bidi, zero-width text, safe
Polish Unicode, secret-read, secret+outbound, prohibition of curl|sh,
curl|sh imperative, TLS bypass, lifecycle hook, safe project-local command,
missing skill path, duplicate active skill, planned missing skill allowed.
