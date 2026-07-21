# Domain Classification

Core subdomains: payment lifecycle, routing, settlement, ledger. Supporting subdomains: ingress, batch, ISO adaptation, signature, reconciliation, egress, case, risk, simulation, reporting, evidence-audit. Generic subdomains: reference data and identity/access. `shared` is a platform module, while `graphql` and the Next.js BFF are technical adapters—not bounded contexts. The implemented/planned distinction and package evidence are in `MODULE-CATALOG.yaml`. This classification directs investment and isolation; it neither changes ownership nor creates aggregates.
