-- =============================================================================
-- V11  Product image — catalogue imagery (UI/UX revamp, Step 2 of 14)
--
-- Deliberately a SEPARATE table from label_image (V4__catalog.sql), not an
-- extension of it. label_image is private, retention-bound OCR-capture
-- evidence: every row is tied to the uploader (uploaded_by_user_id), carries a
-- mandatory retention_due_at deletion obligation, and its purpose is
-- constrained to the label-capture vocabulary (NUTRITION_PANEL/INGREDIENTS/
-- FRONT_PACK). A catalogue hero photo is a different kind of fact — public,
-- non-personal, no deletion countdown — and forcing it through label_image's
-- contract would either fabricate a retention_due_at for data that isn't
-- personal or quietly weaken label_image's own privacy guarantee. So: no
-- retention/deletion-obligation column here, on purpose.
--
-- One row per product is the common case today, but nothing here enforces
-- "exactly one image per product" the way uq_product_current_version does for
-- versions — is_primary exists so a future multi-image gallery has a place to
-- land without a schema change, but only the CURRENT primary image is ever
-- shown (AC scope: no gallery UI yet). What IS enforced, the same way
-- uq_product_current_version is: at most one primary image per product at a
-- time, as a database invariant rather than an application convention.
--
-- storage_key is generated server-side ({productId}/{contentHash}.{ext}) and
-- is never derived from anything a caller/import source sent verbatim — same
-- path-traversal reasoning label_image's own comment documents (Constitution
-- §80). content_hash makes re-ingesting an already-stored image (e.g. a
-- resumed Open Food Facts image backfill) a cheap, safe no-op instead of a
-- duplicate upload.
-- =============================================================================

CREATE TABLE product_image (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id          uuid NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    storage_key         text NOT NULL UNIQUE,
    source              text NOT NULL
                          CHECK (source IN ('OFF_IMPORT','BRAND_PUBLISHED','USER_SUBMITTED',
                                            'HUMAN_CROSSCHECK')),
    status              text NOT NULL DEFAULT 'UNVERIFIED'
                          CHECK (status IN ('UNVERIFIED','VERIFIED','REJECTED')),
    content_hash        text NOT NULL,
    mime_type           text NOT NULL,
    byte_size           bigint NOT NULL CHECK (byte_size > 0),
    width               int,
    height              int,
    is_primary          boolean NOT NULL DEFAULT true,
    captured_at         timestamptz NOT NULL DEFAULT now(),
    verified_at         timestamptz,
    verified_by_user_id uuid REFERENCES user_account(id) ON DELETE SET NULL,
    created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_product_image_product ON product_image (product_id);

-- At most one primary (hero) image per product — mirrors
-- uq_product_current_version's partial-unique-index pattern in V4__catalog.sql.
CREATE UNIQUE INDEX uq_product_image_primary ON product_image (product_id) WHERE is_primary;

-- Re-ingesting the same bytes for the same product (a resumed import) must be
-- a cheap lookup, not a table scan.
CREATE INDEX ix_product_image_content_hash ON product_image (product_id, content_hash);

COMMENT ON COLUMN product_image.storage_key IS
    'Generated server-side from {productId}/{contentHash}.{ext} — never derived '
    'from caller/import input (path-traversal risk, see label_image).';

COMMENT ON TABLE product_image IS
    'Catalogue imagery — public, non-personal. No retention/deletion-obligation '
    'column, unlike label_image, because this is not private data.';
