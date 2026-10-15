# Source 005 — ICMR-NIN Dietary Guidelines for Indians (2024)

**Publisher:** ICMR — National Institute of Nutrition
**Title:** *Dietary Guidelines for Indians*
**Year:** 2024-05-01
**Document URI:** `https://www.nin.res.in/downloads/DietaryGuidelinesforNINwebsite.pdf`
**Already seeded in this repository** as `reference_standard` code `ICMR_NIN_DGI_2024` (`backend/src/main/resources/db/migration/V1__extensions_and_reference_data.sql`), evidence level A, jurisdiction IN — this file exists to carry the quoted guidance the evidence pipeline (D-025) requires alongside that row; it does not re-seed or change the row.

**Scope note as already recorded in the migration (quoted verbatim):**

> "Indian dietary guidance including added-sugar, salt, fat and balanced-diet context. Preferred over non-Indian frameworks for Indian dietary patterns."

## Status of this source: PARTIAL — flagged, not filled in

The PDF at the URI above (4.9 MB) was downloaded and inspected for this evidence pack. It is an **image-based PDF** — a document-block transcription attempt through the Claude API returned only "The provided content appears to be a corrupted or improperly extracted PDF file... the actual dietary guidelines content is not legible in the format provided," meaning the pages did not render as readable text through that path either. Getting a reliable, page-referenced quotation of this document's specific numeric guidance (e.g. any gram-per-day figure for added sugar or salt, or its definition of ultra-processed/HFSS foods) requires either:

1. re-rendering and reading the PDF's own pages as images directly (the same method that worked for `source-001` and `source-002` in this pack), which was not completed for this document in this pass, or
2. a print/alternate copy of the document supplied by the reviewer.

**Nothing from this source is quoted in `rules-v2.md`.** Where the draft rules propose a `NOVA_GROUP = 4` rule citing "ICMR-NIN DGI 2024's guidance on minimising ultra-processed foods," that citation is presented to the reviewer as **unverified** — the rule spec says so explicitly and the reviewer should either strike it, approve it against their own copy of the guideline, or ask for the page-image transcription to be completed before approval. This is the honest outcome per the evidence pipeline's own rule: a value that cannot be read is left blank and flagged, never guessed (D-025).

**Retrieved (for this evidence file):** 2026-09-13.
