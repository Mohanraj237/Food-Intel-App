-- =============================================================================
-- V6  Capture — manual-entry contribution queue (Phase 3)
--
-- system-architecture.md §6 describes one contribution queue fed by two intake
-- paths: OCR-confirmed extraction, and manual entry when a scan or search comes
-- up empty. This migration is the manual-entry path's own table. Both paths end
-- the same way — PENDING, ops review, catalogue (never auto-published) — but
-- nothing here writes to `product` / `product_version` / any other catalog
-- table, and nothing here makes a contribution visible in search. The ops
-- review console that drains this queue is a separate, later piece of work
-- (docs/product/post-mvp-backlog.md).
--
-- category_code REFERENCES category(code) rather than category(id), for the
-- same reason V5__scoring.sql's scoring_rule.applies_to_category does: it lets
-- the API layer compare a plain code string without a UUID lookup, and it
-- keeps this module free of any JPA relationship into catalog's entities (the
-- same raw id/code convention D-023 established for cross-module facts).
--
-- pack_size_value is numeric(10,2) with the same "NULL or positive" CHECK as
-- product.pack_size_value in V4__catalog.sql — a contribution is still a fact
-- about a pack size, even before it is reviewed.
--
-- nutrients is a jsonb array, not a join table: a contribution is a single
-- unreviewed submission, not yet the per-field-provenance model V4's
-- product_nutrient implements for verified facts. Shape:
--   [{"nutrientCode": "...", "amountPer100": ..., "amountPerServing": ...}, ...]
-- =============================================================================

CREATE TABLE product_contribution (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    submitted_by_user_id    uuid NOT NULL REFERENCES user_account(id),
    gtin                    text,
    brand_name              text,
    product_name            text NOT NULL,
    category_code           text REFERENCES category(code),
    pack_size_value         numeric(10,2) CHECK (pack_size_value IS NULL OR pack_size_value > 0),
    pack_size_unit          text,
    serving_declared_text   text,
    nutrients               jsonb NOT NULL,   -- [{nutrientCode, amountPer100, amountPerServing}]
    ingredients_raw_text    text,
    allergen_codes          text[] NOT NULL DEFAULT '{}',
    status                  text NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    submitted_at            timestamptz NOT NULL,
    reviewed_at             timestamptz,
    reviewed_by             text,
    review_notes            text
);

CREATE INDEX ix_product_contribution_status ON product_contribution (status, submitted_at);
