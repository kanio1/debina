# Rail-Specific Modeling

Common business intent stays rail-neutral. ISO messages, headers, envelopes, and files are message/exchange-layer artifacts. CSM submissions, batches, bulks, cycles, positions, and settlement instructions are rail-specific only when an applicable authoritative source identifies them. Model rail differences behind explicit profiles/adapters; never key the frozen Settlement Profile Engine by CSM name. Public documentation may support a simulation; unavailable participant rules remain `[OPEN-QUESTION]` or `[ASSUMPTION]`.
