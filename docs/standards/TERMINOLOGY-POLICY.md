# Terminology Policy

Use ISO/EPC/rail terminology only with its source and applicable rail/version. A project term is allowed when it names a Debina implementation construct, and must be tagged `[PROJECT-ADR]` or `[PROJECT-SIMULATION]`. Do not give a project convenience name the authority of a scheme term.

Terms are singular, defined once in the enterprise concept model, and qualified by layer where ambiguous (for example `BusinessMessage` versus `BusinessMessageEnvelope`). A new synonym, acronym, or overloaded word requires a source-registry reference or `[ASSUMPTION]` plus review trigger. Terminology does not itself create an aggregate, API, event, table, or module.
