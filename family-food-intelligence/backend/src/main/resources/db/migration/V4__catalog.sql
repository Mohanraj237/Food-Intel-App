-- =============================================================================
-- V4  Catalogue, search and capture
--
-- Binding decisions implemented here:
--   D-014  product = stable identity, product_version = facts. Nothing is ever
--          overwritten; a change creates a new version and supersedes the old.
--   D-015  provenance is per FIELD. product_nutrient, product_ingredient and
--          product_allergen each carry a NOT NULL source_id.
--   D-016  the permitted source list is a CHECK constraint, not a convention.
--   D-010  serving_plausible is stored so the UI can state that a declared
--          serving is unrealistic for its category.
--
-- Three properties are enforced by the database rather than by application
-- convention, because each is a promise the product makes to a user:
--
--   1. A nutrient row cannot exist without a value (ck_nutrient_has_value).
--      A missing nutrient is an ABSENT ROW, never a zero. "Zero sugar" and
--      "sugar not declared" are different products (AC-5.2).
--   2. Exactly one current version per product (uq_product_current_version),
--      as a partial unique index. Two current versions is not a state the
--      application can reach even by accident.
--   3. An OPEN conflict forces data_quality = 'CONFLICTING' by trigger
--      (AC-5.3). A disagreement between sources cannot be silently resolved
--      by whichever write happened last.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Brand
--
-- normalized_name is the lower-cased, unaccented form used for matching. It is
-- unique: two brand rows that normalise identically are the same brand and
-- merging them later is far more expensive than refusing the duplicate now.
-- -----------------------------------------------------------------------------
CREATE TABLE brand (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name            text NOT NULL,
    normalized_name text NOT NULL,
    owner_company   text,
    country_code    text NOT NULL DEFAULT 'IN',
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_brand_normalized UNIQUE (normalized_name)
);

CREATE INDEX ix_brand_trgm ON brand USING gin (normalized_name gin_trgm_ops);


-- -----------------------------------------------------------------------------
-- Brand aliases
--
-- Not in the original table list, and load-bearing anyway: PANTRY 06 §2 and
-- database-design §12 both conclude that for an Indian catalogue the alias table
-- matters more than the search engine. "Britania", "Amool", "Maggie" and
-- "Kellogs" are what people actually type. Modelled on ingredient_alias.
-- -----------------------------------------------------------------------------
CREATE TABLE brand_alias (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id         uuid NOT NULL REFERENCES brand(id) ON DELETE CASCADE,
    alias            text NOT NULL,
    normalized_alias text NOT NULL,
    language         text NOT NULL DEFAULT 'en',
    CONSTRAINT uq_brand_alias UNIQUE (brand_id, normalized_alias)
);

CREATE INDEX ix_brand_alias_trgm ON brand_alias USING gin (normalized_alias gin_trgm_ops);
CREATE INDEX ix_brand_alias_brand ON brand_alias (brand_id);


-- -----------------------------------------------------------------------------
-- Product — stable identity only. No fact lives here (D-014).
--
-- search_vector and search_text are both maintained by trigger and never
-- written by the application. search_text is the trigram surface: brand, every
-- brand alias, the product name and the category name, flattened. Full-text
-- search finds exact tokens; the trigram surface is what tolerates a typo and
-- what makes an alias findable (AC-4.1).
-- -----------------------------------------------------------------------------
CREATE TABLE product (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id               uuid REFERENCES brand(id),
    name                   text NOT NULL,
    normalized_name        text NOT NULL,
    pack_size_value        numeric(10,2) CHECK (pack_size_value IS NULL OR pack_size_value > 0),
    pack_size_unit         text,
    category_id            uuid REFERENCES category(id),
    status                 text NOT NULL DEFAULT 'DISCOVERED'
                             CHECK (status IN ('DISCOVERED','ACTIVE','REJECTED','MERGED')),
    merged_into_product_id uuid REFERENCES product(id),
    search_vector          tsvector,
    search_text            text NOT NULL DEFAULT '',
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_product_fts       ON product USING gin (search_vector);
CREATE INDEX ix_product_name_trgm ON product USING gin (normalized_name gin_trgm_ops);
CREATE INDEX ix_product_text_trgm ON product USING gin (search_text gin_trgm_ops);
CREATE INDEX ix_product_brand     ON product (brand_id);
CREATE INDEX ix_product_category  ON product (category_id);
CREATE INDEX ix_product_status    ON product (status);

COMMENT ON COLUMN product.search_text IS
    'Denormalised trigram surface: brand + aliases + name + category. Maintained '
    'by trigger. Never written by the application.';


-- -----------------------------------------------------------------------------
-- Search document maintenance.
--
-- 'simple' rather than 'english': Indian brand names are not English words and
-- stemming actively harms them — "Bikaji" and "Bikaj" are not the same brand,
-- and an English stemmer has opinions about neither (database-design §12).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION product_search_refresh()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_brand    text := '';
    v_aliases  text := '';
    v_category text := '';
BEGIN
    IF NEW.brand_id IS NOT NULL THEN
        SELECT b.name INTO v_brand FROM brand b WHERE b.id = NEW.brand_id;
        SELECT coalesce(string_agg(a.alias, ' '), '') INTO v_aliases
          FROM brand_alias a WHERE a.brand_id = NEW.brand_id;
    END IF;

    IF NEW.category_id IS NOT NULL THEN
        SELECT c.name INTO v_category FROM category c WHERE c.id = NEW.category_id;
    END IF;

    NEW.search_vector :=
          setweight(to_tsvector('simple', unaccent(coalesce(v_brand, '')  || ' '
                                                || coalesce(v_aliases, ''))), 'A')
       || setweight(to_tsvector('simple', unaccent(coalesce(NEW.name, ''))), 'B')
       || setweight(to_tsvector('simple', unaccent(coalesce(v_category, ''))), 'C');

    NEW.search_text := lower(unaccent(
            coalesce(v_brand, '')    || ' ' || coalesce(v_aliases, '') || ' ' ||
            coalesce(NEW.name, '')   || ' ' || coalesce(v_category, '')));

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_product_search
    BEFORE INSERT OR UPDATE OF name, brand_id, category_id ON product
    FOR EACH ROW EXECUTE FUNCTION product_search_refresh();

-- A brand rename or a new alias must reach the products that depend on it,
-- otherwise an alias added after its products is invisible to search — which is
-- exactly the order in which real data arrives.
CREATE OR REPLACE FUNCTION product_search_refresh_for_brand()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_brand uuid;
BEGIN
    IF TG_OP = 'DELETE' THEN
        v_brand := CASE WHEN TG_TABLE_NAME = 'brand' THEN OLD.id ELSE OLD.brand_id END;
    ELSE
        v_brand := CASE WHEN TG_TABLE_NAME = 'brand' THEN NEW.id ELSE NEW.brand_id END;
    END IF;

    -- Touching name re-fires the BEFORE trigger above, which recomputes both
    -- documents from the current brand and alias rows.
    UPDATE product SET name = name WHERE brand_id = v_brand;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_brand_search_cascade
    AFTER UPDATE OF name ON brand
    FOR EACH ROW EXECUTE FUNCTION product_search_refresh_for_brand();

CREATE TRIGGER trg_brand_alias_search_cascade
    AFTER INSERT OR UPDATE OR DELETE ON brand_alias
    FOR EACH ROW EXECUTE FUNCTION product_search_refresh_for_brand();


-- -----------------------------------------------------------------------------
-- Barcodes
--
-- gtin is deliberately NOT unique. Real Indian catalogues contain reused and
-- mis-assigned GTINs, and a unique constraint would force the import to discard
-- one of two true rows. Instead a second active mapping raises conflict_flag on
-- every row for that GTIN and the lookup returns a disambiguation result rather
-- than guessing (AC-4.4).
-- -----------------------------------------------------------------------------
CREATE TABLE product_barcode (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id    uuid NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    gtin          text NOT NULL CHECK (gtin ~ '^[0-9]{8,14}$'),
    gtin_type     text NOT NULL
                    CHECK (gtin_type IN ('EAN13','EAN8','UPCA','ITF14')),
    is_primary    boolean NOT NULL DEFAULT false,
    is_active     boolean NOT NULL DEFAULT true,
    source        text,
    added_at      timestamptz NOT NULL DEFAULT now(),
    conflict_flag boolean NOT NULL DEFAULT false,
    CONSTRAINT uq_product_gtin UNIQUE (product_id, gtin)
);

CREATE INDEX ix_barcode_gtin ON product_barcode (gtin);
CREATE INDEX ix_barcode_product ON product_barcode (product_id);

COMMENT ON COLUMN product_barcode.gtin IS
    'Deliberately not unique. See trg_barcode_conflict and AC-4.4.';

CREATE OR REPLACE FUNCTION product_barcode_flag_conflicts()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_gtin  text;
    v_dupes boolean;
BEGIN
    IF TG_OP = 'DELETE' THEN
        v_gtin := OLD.gtin;
    ELSE
        v_gtin := NEW.gtin;
    END IF;

    SELECT count(DISTINCT product_id) > 1 INTO v_dupes
      FROM product_barcode
     WHERE gtin = v_gtin AND is_active;

    UPDATE product_barcode
       SET conflict_flag = v_dupes
     WHERE gtin = v_gtin
       AND conflict_flag IS DISTINCT FROM v_dupes;

    RETURN NULL;
END;
$$;

-- pg_trigger_depth() guards the recursive UPDATE the function performs on its
-- own table.
CREATE TRIGGER trg_barcode_conflict
    AFTER INSERT OR UPDATE OR DELETE ON product_barcode
    FOR EACH ROW
    WHEN (pg_trigger_depth() < 2)
    EXECUTE FUNCTION product_barcode_flag_conflicts();


-- -----------------------------------------------------------------------------
-- Label images
--
-- Declared before product_version because a version may reference the image it
-- was captured from.
--
-- storage_key is generated server-side and is never derived from anything the
-- client sent — a user-supplied filename in a storage key is a path-traversal
-- primitive (Constitution §80). retention_due_at is set at upload, not by a
-- later policy sweep, so the deletion obligation exists from the first moment
-- the row does.
-- -----------------------------------------------------------------------------
CREATE TABLE label_image (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    storage_key        text NOT NULL UNIQUE,
    content_type       text NOT NULL,
    byte_size          bigint NOT NULL CHECK (byte_size > 0),
    width              int,
    height             int,
    checksum_sha256    text NOT NULL,
    purpose            text NOT NULL
                         CHECK (purpose IN ('NUTRITION_PANEL','INGREDIENTS','FRONT_PACK')),
    uploaded_by_user_id uuid REFERENCES user_account(id) ON DELETE SET NULL,
    uploaded_at        timestamptz NOT NULL DEFAULT now(),
    deleted_at         timestamptz,
    retention_due_at   timestamptz NOT NULL
);

CREATE INDEX ix_label_image_retention ON label_image (retention_due_at)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_label_image_uploader  ON label_image (uploaded_by_user_id);


-- -----------------------------------------------------------------------------
-- Product version — where every fact lives (D-014)
-- -----------------------------------------------------------------------------
CREATE TABLE product_version (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id            uuid NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    version_no            int NOT NULL CHECK (version_no > 0),
    status                text NOT NULL
                            CHECK (status IN ('DISCOVERED','OCR_PARSED','NEEDS_VERIFICATION',
                                              'VERIFIED','STALE','SUPERSEDED','REJECTED')),
    is_current            boolean NOT NULL DEFAULT false,
    effective_from        timestamptz NOT NULL DEFAULT now(),
    effective_to          timestamptz,
    serving_size_g        numeric(10,3) CHECK (serving_size_g IS NULL OR serving_size_g > 0),
    serving_size_declared text,
    serving_plausible     boolean,
    servings_per_pack     numeric(10,2)
                            CHECK (servings_per_pack IS NULL OR servings_per_pack > 0),
    ingredients_raw       text,
    ingredients_language  text,
    label_image_id        uuid REFERENCES label_image(id) ON DELETE SET NULL,
    capture_method        text NOT NULL
                            CHECK (capture_method IN ('LABEL_PHOTO','BRAND_PUBLISHED','OFF_IMPORT',
                                                      'USER_SUBMITTED','HUMAN_CROSSCHECK','MANUAL_ENTRY')),
    captured_at           timestamptz NOT NULL DEFAULT now(),
    captured_by_user_id   uuid REFERENCES user_account(id) ON DELETE SET NULL,
    verified_at           timestamptz,
    verified_by_user_id   uuid REFERENCES user_account(id) ON DELETE SET NULL,
    verification_pass     int NOT NULL DEFAULT 0 CHECK (verification_pass >= 0),
    data_quality          text NOT NULL
                            CHECK (data_quality IN ('VERIFIED','RECENT','STALE','CONFLICTING',
                                                    'NEEDS_REVIEW','UNVERIFIED')),
    freshness_due_at      timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_version_no UNIQUE (product_id, version_no)
);

-- D-014, expressed as a database invariant rather than a code comment.
CREATE UNIQUE INDEX uq_product_current_version
    ON product_version (product_id) WHERE is_current;

CREATE INDEX ix_version_product   ON product_version (product_id);
CREATE INDEX ix_version_status    ON product_version (status);
CREATE INDEX ix_version_quality   ON product_version (data_quality);
CREATE INDEX ix_version_freshness ON product_version (freshness_due_at) WHERE is_current;

COMMENT ON COLUMN product_version.serving_plausible IS
    'false when the declared serving falls outside the category typical range. '
    'Stored rather than rejected: an unrealistic declared serving is a fact about '
    'the pack that the user must be told (D-010, AC-5.7).';


-- -----------------------------------------------------------------------------
-- Per-field provenance (D-015)
--
-- source_type is a CHECK rather than free text because D-016 makes the list of
-- permitted sources a decision, not a configuration detail. Automated
-- e-commerce scraping has no representation here and therefore cannot be
-- recorded as a source even by mistake.
-- -----------------------------------------------------------------------------
CREATE TABLE product_source (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_version_id  uuid NOT NULL REFERENCES product_version(id) ON DELETE CASCADE,
    source_type         text NOT NULL
                          CHECK (source_type IN ('LABEL_PHOTO','BRAND_PUBLISHED','OPEN_FOOD_FACTS',
                                                 'FSSAI','ICMR_NIN','USER_SUBMITTED',
                                                 'HUMAN_CROSSCHECK','MANUAL_ENTRY')),
    source_ref          text,
    license_note        text,
    captured_at         timestamptz NOT NULL DEFAULT now(),
    captured_by_user_id uuid REFERENCES user_account(id) ON DELETE SET NULL,
    confidence          numeric(4,3) CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1)
);

CREATE INDEX ix_source_version ON product_source (product_version_id);

COMMENT ON COLUMN product_source.license_note IS
    'ODbL attribution obligation is recorded per source row, so the obligation '
    'travels with the fact rather than living in a policy document (D-016).';


CREATE TABLE product_nutrient (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_version_id uuid NOT NULL REFERENCES product_version(id) ON DELETE CASCADE,
    nutrient_code      text NOT NULL REFERENCES nutrient(code),
    amount_per_100     numeric(12,4),
    amount_per_serving numeric(12,4),
    unit               text NOT NULL,
    basis_per_100      text CHECK (basis_per_100 IN ('PER_100G','PER_100ML')),
    declared_basis     text NOT NULL
                         CHECK (declared_basis IN ('PER_100G','PER_100ML','PER_SERVING','BOTH')),
    is_estimated       boolean NOT NULL DEFAULT false,
    confidence         numeric(4,3) CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
    source_id          uuid NOT NULL REFERENCES product_source(id),
    CONSTRAINT uq_version_nutrient UNIQUE (product_version_id, nutrient_code),
    CONSTRAINT ck_nutrient_non_negative CHECK (
        (amount_per_100 IS NULL OR amount_per_100 >= 0)
        AND (amount_per_serving IS NULL OR amount_per_serving >= 0)),
    -- The row cannot exist without a value. "Not declared" is the absence of a
    -- row, and INSUFFICIENT_DATA depends on that distinction (AC-5.2).
    CONSTRAINT ck_nutrient_has_value CHECK (
        amount_per_100 IS NOT NULL OR amount_per_serving IS NOT NULL)
);

CREATE INDEX ix_nutrient_version ON product_nutrient (product_version_id);

COMMENT ON CONSTRAINT ck_nutrient_has_value ON product_nutrient IS
    'A nutrient the pack does not declare is an absent row, never a zero.';


-- -----------------------------------------------------------------------------
-- Ingredients
-- -----------------------------------------------------------------------------
CREATE TABLE ingredient (
    code              text PRIMARY KEY,
    canonical_name    text NOT NULL,
    function_category text,
    ins_number        text,
    evidence_status   text NOT NULL DEFAULT 'NONE'
                        CHECK (evidence_status IN ('NONE','EMERGING','ESTABLISHED')),
    explanation_key   text
);

CREATE TABLE ingredient_alias (
    id               text PRIMARY KEY DEFAULT gen_random_uuid()::text,
    ingredient_code  text NOT NULL REFERENCES ingredient(code) ON DELETE CASCADE,
    alias            text NOT NULL,
    normalized_alias text NOT NULL,
    language         text NOT NULL DEFAULT 'en',
    CONSTRAINT uq_ingredient_alias UNIQUE (ingredient_code, normalized_alias)
);

CREATE INDEX ix_ingredient_alias_trgm
    ON ingredient_alias USING gin (normalized_alias gin_trgm_ops);


CREATE TABLE product_ingredient (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_version_id uuid NOT NULL REFERENCES product_version(id) ON DELETE CASCADE,
    ingredient_code    text REFERENCES ingredient(code),   -- NULL while unmapped
    raw_text           text NOT NULL,
    position           int NOT NULL CHECK (position > 0),
    is_additive        boolean NOT NULL DEFAULT false,
    source_id          uuid NOT NULL REFERENCES product_source(id),
    CONSTRAINT uq_ingredient_position UNIQUE (product_version_id, position)
);

CREATE INDEX ix_product_ingredient_version ON product_ingredient (product_version_id);

COMMENT ON COLUMN product_ingredient.raw_text IS
    'Verbatim label text. Untrusted: never interpolated into a model prompt '
    'outside the delimited untrusted-data envelope (D-011).';


-- -----------------------------------------------------------------------------
-- Allergens
--
-- declaration_type has no FREE_FROM value and never will. We report what the
-- label declares; manufacturing cross-contamination is outside what a label can
-- tell us and outside what this product may claim (AC-5.8).
-- -----------------------------------------------------------------------------
CREATE TABLE product_allergen (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_version_id uuid NOT NULL REFERENCES product_version(id) ON DELETE CASCADE,
    allergen_code      text NOT NULL REFERENCES allergen(code),
    declaration_type   text NOT NULL
                         CHECK (declaration_type IN ('CONTAINS','MAY_CONTAIN','NOT_DECLARED')),
    source_id          uuid NOT NULL REFERENCES product_source(id),
    confidence         numeric(4,3) CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
    CONSTRAINT uq_version_allergen UNIQUE (product_version_id, allergen_code)
);

CREATE INDEX ix_product_allergen_version ON product_allergen (product_version_id);

COMMENT ON CONSTRAINT product_allergen_declaration_type_check ON product_allergen IS
    'There is no FREE_FROM. The app never claims a product is free from an allergen.';


-- -----------------------------------------------------------------------------
-- Conflict, correction, change
-- -----------------------------------------------------------------------------
CREATE TABLE product_conflict (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_version_id  uuid NOT NULL REFERENCES product_version(id) ON DELETE CASCADE,
    field_path          text NOT NULL,
    value_a             text,
    source_a_id         uuid REFERENCES product_source(id) ON DELETE SET NULL,
    value_b             text,
    source_b_id         uuid REFERENCES product_source(id) ON DELETE SET NULL,
    status              text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED')),
    resolved_at         timestamptz,
    resolved_by_user_id uuid REFERENCES user_account(id) ON DELETE SET NULL,
    resolution_note     text,
    created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_conflict_version ON product_conflict (product_version_id);
CREATE INDEX ix_conflict_open    ON product_conflict (product_version_id)
    WHERE status = 'OPEN';

-- AC-5.3: an OPEN conflict forces CONFLICTING, and closing the last one
-- releases the version back to NEEDS_REVIEW. Implemented as a trigger so that
-- no write path — application, migration, seed or manual fix — can produce a
-- version that is quietly presented as sound while a disagreement is open.
CREATE OR REPLACE FUNCTION product_conflict_apply_quality()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_version uuid;
    v_open    boolean;
BEGIN
    IF TG_OP = 'DELETE' THEN
        v_version := OLD.product_version_id;
    ELSE
        v_version := NEW.product_version_id;
    END IF;

    SELECT EXISTS (SELECT 1 FROM product_conflict
                    WHERE product_version_id = v_version AND status = 'OPEN')
      INTO v_open;

    IF v_open THEN
        UPDATE product_version
           SET data_quality = 'CONFLICTING',
               status = CASE WHEN status IN ('SUPERSEDED','REJECTED')
                             THEN status ELSE 'NEEDS_VERIFICATION' END
         WHERE id = v_version AND data_quality <> 'CONFLICTING';
    ELSE
        UPDATE product_version
           SET data_quality = 'NEEDS_REVIEW'
         WHERE id = v_version AND data_quality = 'CONFLICTING';
    END IF;

    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_conflict_quality
    AFTER INSERT OR UPDATE OR DELETE ON product_conflict
    FOR EACH ROW EXECUTE FUNCTION product_conflict_apply_quality();


CREATE TABLE data_correction (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_version_id  uuid NOT NULL REFERENCES product_version(id) ON DELETE CASCADE,
    field_path          text NOT NULL,
    submitted_value     text,
    note                text,
    submitted_by_user_id uuid REFERENCES user_account(id) ON DELETE SET NULL,
    status              text NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
    reviewed_by_user_id uuid REFERENCES user_account(id) ON DELETE SET NULL,
    reviewed_at         timestamptz,
    resolution_note     text,
    created_at          timestamptz NOT NULL DEFAULT now()
);

-- The 48-hour review SLA is only meetable if the queue is cheap to read.
CREATE INDEX ix_correction_queue ON data_correction (created_at)
    WHERE status = 'PENDING';
CREATE INDEX ix_correction_version ON data_correction (product_version_id);


CREATE TABLE product_change_log (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id           uuid NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    from_version_id      uuid REFERENCES product_version(id) ON DELETE SET NULL,
    to_version_id        uuid REFERENCES product_version(id) ON DELETE SET NULL,
    changed_fields       jsonb NOT NULL DEFAULT '{}'::jsonb,
    change_reason        text,
    materially_changed   boolean NOT NULL DEFAULT false,
    detected_at          timestamptz NOT NULL DEFAULT now(),
    detected_by_user_id  uuid REFERENCES user_account(id) ON DELETE SET NULL
);

CREATE INDEX ix_change_log_product ON product_change_log (product_id, detected_at DESC);


-- -----------------------------------------------------------------------------
-- Capture — OCR extraction and its mandatory confirmation
--
-- There is no foreign key from ocr_extraction to product_version, and there
-- never will be. The only column that can connect an extraction to the
-- catalogue is ocr_confirmation.resulted_in_product_version_id, so the human
-- step is not a policy the application enforces — it is the only edge in the
-- graph (database-design §10, AC-4.6).
-- -----------------------------------------------------------------------------
CREATE TABLE ocr_extraction (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    label_image_id     uuid NOT NULL REFERENCES label_image(id) ON DELETE CASCADE,
    provider           text NOT NULL,
    provider_model     text,
    status             text NOT NULL
                         CHECK (status IN ('PENDING','SUCCEEDED','FAILED','LOW_CONFIDENCE')),
    raw_text           text,
    proposal           jsonb NOT NULL DEFAULT '{}'::jsonb,
    overall_confidence numeric(4,3)
                         CHECK (overall_confidence IS NULL OR overall_confidence BETWEEN 0 AND 1),
    error_code         text,
    started_at         timestamptz NOT NULL DEFAULT now(),
    completed_at       timestamptz
);

CREATE INDEX ix_extraction_image  ON ocr_extraction (label_image_id);
CREATE INDEX ix_extraction_status ON ocr_extraction (status, started_at DESC);
-- raw_text is purged after 30 days (database-design §13).
CREATE INDEX ix_extraction_purge  ON ocr_extraction (started_at) WHERE raw_text IS NOT NULL;

COMMENT ON COLUMN ocr_extraction.proposal IS
    'Proposed fields with PER-FIELD confidence. A proposal, never a fact: nothing '
    'here may reach a verdict or the catalogue without an ocr_confirmation row.';


CREATE TABLE ocr_confirmation (
    id                             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ocr_extraction_id              uuid NOT NULL UNIQUE
                                     REFERENCES ocr_extraction(id) ON DELETE CASCADE,
    confirmed_by_user_id           uuid NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    confirmed_fields               jsonb NOT NULL DEFAULT '{}'::jsonb,
    edited_fields                  jsonb NOT NULL DEFAULT '{}'::jsonb,
    resulted_in_product_version_id uuid REFERENCES product_version(id) ON DELETE SET NULL,
    contributed_to_queue           boolean NOT NULL DEFAULT false,
    confirmed_at                   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_confirmation_user ON ocr_confirmation (confirmed_by_user_id);


-- -----------------------------------------------------------------------------
-- Ingredient reference rows.
--
-- Reference data, not fixtures: the additive vocabulary is identical in every
-- environment and the application cannot map an ingredient without it.
-- evidence_status is deliberately conservative — an ingredient is not labelled
-- as carrying evidence unless a citable standard exists (D-009).
-- -----------------------------------------------------------------------------
INSERT INTO ingredient (code, canonical_name, function_category, ins_number, evidence_status, explanation_key) VALUES
    ('WHEAT_FLOUR',      'Refined wheat flour (maida)', 'CEREAL',       NULL,   'NONE',      'ingredient.wheat_flour'),
    ('WHOLE_WHEAT_FLOUR','Whole wheat flour (atta)',    'CEREAL',       NULL,   'NONE',      'ingredient.whole_wheat_flour'),
    ('SUGAR',            'Sugar',                       'SWEETENER',    NULL,   'ESTABLISHED','ingredient.sugar'),
    ('INVERT_SYRUP',     'Invert sugar syrup',          'SWEETENER',    NULL,   'ESTABLISHED','ingredient.invert_syrup'),
    ('LIQUID_GLUCOSE',   'Liquid glucose',              'SWEETENER',    NULL,   'ESTABLISHED','ingredient.liquid_glucose'),
    ('PALM_OIL',         'Palm oil',                    'FAT',          NULL,   'EMERGING',  'ingredient.palm_oil'),
    ('SUNFLOWER_OIL',    'Sunflower oil',               'FAT',          NULL,   'NONE',      'ingredient.sunflower_oil'),
    ('MILK_SOLIDS',      'Milk solids',                 'DAIRY',        NULL,   'NONE',      'ingredient.milk_solids'),
    ('COCOA_SOLIDS',     'Cocoa solids',                'COCOA',        NULL,   'NONE',      'ingredient.cocoa_solids'),
    ('SALT',             'Iodised salt',                'SEASONING',    NULL,   'ESTABLISHED','ingredient.salt'),
    ('MALT_EXTRACT',     'Malt extract',                'CEREAL',       NULL,   'NONE',      'ingredient.malt_extract'),
    ('GRAM_FLOUR',       'Gram flour (besan)',          'PULSE',        NULL,   'NONE',      'ingredient.gram_flour'),
    ('POTATO',           'Potato',                      'VEGETABLE',    NULL,   'NONE',      'ingredient.potato'),
    ('RICE_FLOUR',       'Rice flour',                  'CEREAL',       NULL,   'NONE',      'ingredient.rice_flour'),
    ('CORN_FLOUR',       'Corn flour',                  'CEREAL',       NULL,   'NONE',      'ingredient.corn_flour'),
    ('OATS',             'Rolled oats',                 'CEREAL',       NULL,   'NONE',      'ingredient.oats'),
    ('PEANUT',           'Peanut',                      'NUT',          NULL,   'NONE',      'ingredient.peanut'),
    ('CASHEW',           'Cashew',                      'NUT',          NULL,   'NONE',      'ingredient.cashew'),
    ('TOMATO_PASTE',     'Tomato paste',                'VEGETABLE',    NULL,   'NONE',      'ingredient.tomato_paste'),
    ('ACIDITY_REG_330',  'Citric acid',                 'ACIDITY_REGULATOR','330','NONE',    'ingredient.ins_330'),
    ('RAISING_AGENT_500','Sodium bicarbonate',          'RAISING_AGENT','500',  'NONE',      'ingredient.ins_500'),
    ('EMULSIFIER_322',   'Lecithin',                    'EMULSIFIER',   '322',  'NONE',      'ingredient.ins_322'),
    ('EMULSIFIER_471',   'Mono- and diglycerides of fatty acids', 'EMULSIFIER','471','NONE', 'ingredient.ins_471'),
    ('FLAVOUR_NATURAL',  'Natural flavouring substances','FLAVOURING',  NULL,   'NONE',      'ingredient.flavour_natural'),
    ('COLOUR_150D',      'Caramel colour (sulphite ammonia)','COLOUR',  '150d', 'NONE',      'ingredient.ins_150d'),
    ('SWEETENER_955',    'Sucralose',                   'SWEETENER',    '955',  'NONE',      'ingredient.ins_955'),
    ('SWEETENER_950',    'Acesulfame potassium',        'SWEETENER',    '950',  'NONE',      'ingredient.ins_950'),
    ('PRESERVATIVE_211', 'Sodium benzoate',             'PRESERVATIVE', '211',  'NONE',      'ingredient.ins_211'),
    ('ANTIOXIDANT_319',  'TBHQ',                        'ANTIOXIDANT',  '319',  'NONE',      'ingredient.ins_319'),
    ('MSG_635',          'Disodium ribonucleotides',    'FLAVOUR_ENHANCER','635','NONE',     'ingredient.ins_635');

INSERT INTO ingredient_alias (ingredient_code, alias, normalized_alias, language) VALUES
    ('WHEAT_FLOUR',       'maida',              'maida',             'en'),
    ('WHEAT_FLOUR',       'refined wheat flour','refined wheat flour','en'),
    ('WHOLE_WHEAT_FLOUR', 'atta',               'atta',              'en'),
    ('WHOLE_WHEAT_FLOUR', 'gehun ka atta',      'gehun ka atta',     'hi'),
    ('SUGAR',             'chini',              'chini',             'hi'),
    ('GRAM_FLOUR',        'besan',              'besan',             'en'),
    ('SALT',              'namak',              'namak',             'hi'),
    ('SALT',              'iodised salt',       'iodised salt',      'en'),
    ('EMULSIFIER_322',    'soya lecithin',      'soya lecithin',     'en'),
    ('EMULSIFIER_322',    'ins 322',            'ins 322',           'en'),
    ('ANTIOXIDANT_319',   'ins 319',            'ins 319',           'en'),
    ('COLOUR_150D',       'ins 150d',           'ins 150d',          'en');
