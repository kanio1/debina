# Module Catalogue Notes

The machine-readable source is [MODULE-CATALOG.yaml](MODULE-CATALOG.yaml), verified against Java package names and Flyway schemas at Phase B baseline `f601089`. Ingress and ISO adaptation are implemented responsibilities but remain packaged under `paymentlifecycle`; this is a boundary finding, not authority to refactor. GraphQL and Next.js BFF are adapters, not business bounded contexts.
