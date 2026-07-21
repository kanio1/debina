# Terminology Glossary

Canonical definitions and synonyms are owned by [PAYMENT-CONCEPT-CATALOG.yaml](PAYMENT-CONCEPT-CATALOG.yaml). This index calls out collisions:

| Term | Use | Guardrail |
|---|---|---|
| payment order / instruction / transaction | different business layers | do not assert 1:1 without a use-case/cardinality rule |
| business message / AppHdr / envelope | payload, header and wrapper | preserve identifiers and evidence separately |
| file / batch / bulk / submission / interchange | rail/message/exchange constructs | qualify rail and exact source; never use as synonyms |
| status / finality / delivery / receipt | five separate axes | a pacs.002, dispatch or receipt is not finality |
| return / reversal | opposite-direction payment vs ledger correction | return is never a ledger reversal |
