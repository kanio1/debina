# Source Coverage

Generated: 2026-07-27

## Corpus summary

- Files in archive: 56 (55 corpus files plus top-level package layout count as extracted).
- Local PDFs: 23.
- Registered sources: 57.
- Capability rows: 31.
- R/claims/reporting rows: 20.
- Evidence gaps: 15.
- Source-register entries with `local_exists=yes`: 9; all 9 hashes match their manifest records.
- SHA256SUMS verification: 54 OK, 1 FAILED (`validation_report.json`).
- Expected validation report hash: `b889bb568b7b5f148112be3a39da309e3b9fb1ca617e458265982509d5b62230`.
- Actual hash: `07f1e0fc94079daa0880405f89da21a56b9e463b5526fd336a08bf26803327f5`.

## Classification counts

| Classification | Count |
|---|---|
| PUBLIC-SOURCE | 31 |
| BANK-PRACTICE | 15 |
| PUBLIC-SOURCE-DISCOVERY | 4 |
| FUTURE-NOT-FINAL | 3 |
| PARTICIPANT-DOC-REQUIRED | 3 |
| LEGAL-AUTHORITY | 1 |

## Availability counts

| Availability | Count |
|---|---|
| REMOTE_PUBLIC_DOWNLOAD_REQUIRED | 28 |
| PUBLIC_PAGE_ONLY | 13 |
| LICENSE_REVIEW_LINK_ONLY | 4 |
| ATTACHMENT_URL_RESOLUTION_REQUIRED | 3 |
| PUBLIC_PAGE_WITH_ATTACHMENT | 3 |
| PORTAL_RESTRICTED | 2 |
| DOWNLOAD_ENDPOINT_FAILED_IN_RUNTIME | 1 |
| PARTICIPANT_ACCESS_REQUIRED | 1 |
| FUTURE_NOT_FINAL | 1 |
| LOCAL_EXISTING | 1 |

## Evidence gaps

| Gap | Priority | Missing evidence | Status | Required action |
|---|---|---|---|---|
| GAP-001 | P0 | Final EPC rulebooks and IGs entering into force in November 2027 | Not yet final as of 2026-07-26; consultation cycle completed | Monitor expected November 2026 publication; run formal delta analysis and approval before activating future-dated rules. |
| GAP-002 | P0 | Official ISO 20022 XSD/MDR aligned to every selected message version | Not physically acquired in this corpus | Acquire under approved terms, checksum, register versions and link every mapping/test. |
| GAP-003 | P0 | EPC SDD Technical Validation Subset ZIP/XSD attachments | Public attachment URLs require resolution/download | Download from official IG pages; label as technical subsets, never as complete production/business contracts. |
| GAP-004 | P0 | VOP API YAML ZIP and all executable examples | Public attachment available but not physically downloaded in this runtime | Fetch, checksum, validate syntax and derive contract tests. |
| GAP-005 | P0 | Authenticated EDS operational files/API credentials and certificates | Participant/RVM onboarding required | Provision EDS GUI/API access, certificates, EULA, test data, rotation and incident procedures. |
| GAP-006 | P0 | STEP2 SDD Core participant manuals | Customer portal restricted | Obtain through authorised participant; record service/version/contract applicability. |
| GAP-007 | P1 | STEP2 SDD B2B participant manuals | Customer portal restricted | Acquire only if B2B over STEP2 is selected. |
| GAP-008 | P0 | STEP2 SATP, onboarding and certification evidence | Participant access required | Do not claim adapter production readiness without authorised testing and certification artefacts. |
| GAP-009 | P0 | Exact STEP2 envelopes, ACK/NAK, cycles, cut-offs, warehousing, finality and recovery rules | Not public in sufficient implementation detail | Keep adapter interfaces/evidence gaps only; do not infer from EPC rulebooks or bank MIGs. |
| GAP-010 | P0 | Final VOP rulebook version 2.0 and related artefacts | Expected end November 2026; not final as of analysis date | Monitor publication and run v1.1-to-v2.0 compatibility review. |
| GAP-011 | P1 | Bank-specific SDD service contracts, cut-offs, signing, file naming and country profiles | Only representative public MIGs available | Create one effective-dated channel profile per bank/legal entity/product; never generalise representative MIGs. |
| GAP-012 | P2 | Bank of Ireland public PDFs/XSD licensing approval for redistribution | Link-only in corpus pending licence review | Use exact official links; do not repackage bytes until terms permit. |
| GAP-013 | P0 | National legal interpretation for mandate validity, refund/dispute evidence and retention | Cannot be proven by one EPC document | Create jurisdiction-specific legal claims and retention schedule approved by counsel. |
| GAP-014 | P0 | Actual Debina repository implementation audit for SDD/VOP/EDS modules | Not performed in this document-collection task | Run repository evidence mapping: source claim -> requirement -> flow -> code -> test -> operational evidence. |
| GAP-015 | P1 | New public SDD/VOP PDF bytes in this execution environment | Direct HTTP download unavailable; URLs and downloader supplied | Run downloader in authorised network environment, validate MIME/PDF/XSD/ZIP, hash, render PDFs and update availability. |

## Selective PDF inspection

Rendered and text-inspected samples:

- EPC SCT 2025 Rulebook v1.1;
- EPC SCT Inst 2025 Rulebook v1.1;
- TIPS UDFS R2026.JUN;
- Nordea Corporate Access pain.001.001.09 MIG v1.3.

The samples confirm the authority distinction: EPC scheme rules, TIPS rail specification and Nordea bank-channel profile are not interchangeable.

## Usage policy

Keep PDF bytes outside Debina. Import only approved metadata, source IDs, versions, effective dates, hashes, page/section references and claim maps.
