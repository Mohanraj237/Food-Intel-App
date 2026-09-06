-- =============================================================================
-- V1  Extensions and reference data
--
-- Reference tables are schema, not fixtures: the application cannot function
-- without them and they are identical in every environment.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;    -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pg_trgm;     -- fuzzy product search
CREATE EXTENSION IF NOT EXISTS unaccent;    -- search normalisation


-- -----------------------------------------------------------------------------
-- Nutrients
--
-- Added sugar and total sugar are separate rows and must never be conflated:
-- the WHO free-sugars guidance that drives our headline rule is about added
-- sugar, and Indian panels frequently declare only total sugar.
-- -----------------------------------------------------------------------------
CREATE TABLE nutrient (
    code            text PRIMARY KEY,
    display_name    text NOT NULL,
    canonical_unit  text NOT NULL,
    sort_order      int  NOT NULL,
    is_core         boolean NOT NULL DEFAULT false
);

INSERT INTO nutrient (code, display_name, canonical_unit, sort_order, is_core) VALUES
    ('ENERGY_KCAL',    'Energy',            'kcal', 10, true),
    ('PROTEIN_G',      'Protein',           'g',    20, true),
    ('CARB_G',         'Carbohydrate',      'g',    30, true),
    ('TOTAL_SUGAR_G',  'Total sugar',       'g',    40, true),
    ('ADDED_SUGAR_G',  'Added sugar',       'g',    50, true),
    ('FIBRE_G',        'Dietary fibre',     'g',    60, true),
    ('TOTAL_FAT_G',    'Total fat',         'g',    70, true),
    ('SAT_FAT_G',      'Saturated fat',     'g',    80, true),
    ('TRANS_FAT_G',    'Trans fat',         'g',    90, true),
    ('SODIUM_MG',      'Sodium',            'mg',  100, true),
    ('SALT_G',         'Salt',              'g',   110, false),
    ('CHOLESTEROL_MG', 'Cholesterol',       'mg',  120, false),
    ('CALCIUM_MG',     'Calcium',           'mg',  130, false),
    ('IRON_MG',        'Iron',              'mg',  140, false);


-- -----------------------------------------------------------------------------
-- Allergens — the FSSAI-declarable set.
--
-- There is deliberately no "free from" concept anywhere in the schema. We report
-- what a label declares and state that cross-contamination is not covered.
-- -----------------------------------------------------------------------------
CREATE TABLE allergen (
    code                 text PRIMARY KEY,
    display_name         text NOT NULL,
    is_fssai_declarable  boolean NOT NULL DEFAULT true,
    sort_order           int NOT NULL
);

INSERT INTO allergen (code, display_name, is_fssai_declarable, sort_order) VALUES
    ('MILK',        'Milk',                     true, 10),
    ('EGG',         'Egg',                      true, 20),
    ('FISH',        'Fish',                     true, 30),
    ('CRUSTACEAN',  'Crustacean shellfish',     true, 40),
    ('TREE_NUT',    'Tree nuts',                true, 50),
    ('PEANUT',      'Peanuts',                  true, 60),
    ('WHEAT',       'Wheat',                    true, 70),
    ('GLUTEN',      'Gluten-containing cereals',true, 80),
    ('SOY',         'Soy',                      true, 90),
    ('SESAME',      'Sesame',                   true, 100),
    ('SULPHITE',    'Added sulphites',          true, 110),
    ('MUSTARD',     'Mustard',                  false, 120);


-- -----------------------------------------------------------------------------
-- Reference standards
--
-- D-009: a scoring rule carries a NOT NULL foreign key to this table. A rule for
-- which we cannot cite a named public standard cannot physically be inserted.
-- This is the primary defence against the FMCG-disparagement exposure.
-- -----------------------------------------------------------------------------
CREATE TABLE reference_standard (
    code            text PRIMARY KEY,
    name            text NOT NULL,
    publisher       text NOT NULL,
    jurisdiction    text NOT NULL,
    evidence_level  text NOT NULL CHECK (evidence_level IN ('A','B','C','D')),
    document_uri    text NOT NULL,
    published_on    date,
    retrieved_on    date NOT NULL,
    scope_note      text NOT NULL,
    review_due_on   date
);

COMMENT ON COLUMN reference_standard.evidence_level IS
    'A = regulator/official guidance or systematic review; B = peer-reviewed; '
    'C = emerging/limited, must disclose uncertainty; D = anecdotal, may not drive claims.';

INSERT INTO reference_standard
    (code, name, publisher, jurisdiction, evidence_level, document_uri,
     published_on, retrieved_on, scope_note, review_due_on)
VALUES
    ('WHO_FREE_SUGARS_2015',
     'Guideline: Sugars intake for adults and children',
     'World Health Organization', 'GLOBAL', 'A',
     'https://www.who.int/publications/i/item/9789241549028',
     '2015-03-04', '2026-09-05',
     'Free sugars below 10% of total energy intake, with a conditional recommendation '
     'below 5%. Used for added-sugar thresholds.',
     '2027-09-05'),

    ('WHO_SODIUM_2012',
     'Guideline: Sodium intake for adults and children',
     'World Health Organization', 'GLOBAL', 'A',
     'https://www.who.int/publications/i/item/9789241504836',
     '2012-01-01', '2026-09-05',
     'Adult sodium intake below 2000 mg/day. Used for sodium thresholds.',
     '2027-09-05'),

    ('ICMR_NIN_DGI_2024',
     'Dietary Guidelines for Indians',
     'ICMR - National Institute of Nutrition', 'IN', 'A',
     'https://www.nin.res.in/downloads/DietaryGuidelinesforNINwebsite.pdf',
     '2024-05-01', '2026-09-05',
     'Indian dietary guidance including added-sugar, salt, fat and balanced-diet context. '
     'Preferred over non-Indian frameworks for Indian dietary patterns.',
     '2027-09-05'),

    ('ICMR_NIN_RDA_2020',
     'Nutrient Requirements for Indians (RDA and EAR)',
     'ICMR - National Institute of Nutrition', 'IN', 'A',
     'https://www.nin.res.in/RDA_short_Report_2020.html',
     '2020-01-01', '2026-09-05',
     'Recommended dietary allowances by age and sex for the Indian population. '
     'Used for protein and fibre reference values.',
     '2027-09-05'),

    ('FSSAI_INR',
     'Indian Nutrition Rating / front-of-pack nutrition labelling',
     'Food Safety and Standards Authority of India', 'IN', 'A',
     'https://www.fssai.gov.in/',
     NULL, '2026-09-05',
     'Regulatory front-of-pack rating. Displayed separately and labelled as the '
     'regulator''s rating, never merged with our assessment. Thresholds pending final '
     'notification - no rule may depend on this entry until they are published.',
     '2027-03-01');


-- -----------------------------------------------------------------------------
-- Consent purposes
--
-- Consent is granted per purpose and checked at processing time (D-018), not once
-- at signup. requires_verifiable marks the purposes that involve a child and
-- therefore cannot be satisfied by self-attestation.
-- -----------------------------------------------------------------------------
CREATE TABLE consent_purpose (
    code                 text PRIMARY KEY,
    name                 text NOT NULL,
    description          text NOT NULL,
    requires_verifiable  boolean NOT NULL,
    is_essential         boolean NOT NULL,
    active               boolean NOT NULL DEFAULT true,
    sort_order           int NOT NULL
);

INSERT INTO consent_purpose
    (code, name, description, requires_verifiable, is_essential, sort_order)
VALUES
    ('ACCOUNT',
     'Account and sign-in',
     'Create and operate your account. Required to use the app at all.',
     false, true, 10),

    ('PERSONAL_FIT',
     'Personal food suitability',
     'Use the goals, allergens and preferences you provide to work out how a product '
     'fits you, and to explain why.',
     false, false, 20),

    ('CHILD_FOOD_SUITABILITY',
     'Food suitability for a child in your household',
     'Use the age band, allergens and dietary preferences a guardian provides to work '
     'out how a product fits a child. No consumption tracking, no behavioural profile, '
     'no advertising, no notifications to the child.',
     true, false, 30),

    ('LABEL_IMAGE_PROCESSING',
     'Reading a label you photograph',
     'Process a photograph of a nutrition or ingredient panel to extract its values. '
     'You confirm every extracted value before it is used.',
     false, false, 40),

    ('PRODUCT_IMPROVEMENT',
     'Contributing a product to the catalogue',
     'Add a product you captured to our shared catalogue so other households can find '
     'it. Contains no personal data about you.',
     false, false, 50);


-- -----------------------------------------------------------------------------
-- Consent policy versions
--
-- Every consent record points at the exact policy text version the person saw.
-- A policy change therefore cannot retroactively broaden an existing consent.
-- -----------------------------------------------------------------------------
CREATE TABLE consent_policy_version (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    purpose_code    text NOT NULL REFERENCES consent_purpose(code),
    version         text NOT NULL,
    document_uri    text NOT NULL,
    summary         text NOT NULL,
    effective_from  timestamptz NOT NULL,
    effective_to    timestamptz,
    CONSTRAINT uq_policy_version UNIQUE (purpose_code, version)
);

CREATE INDEX ix_policy_current ON consent_policy_version (purpose_code)
    WHERE effective_to IS NULL;

INSERT INTO consent_policy_version
    (purpose_code, version, document_uri, summary, effective_from)
VALUES
    ('ACCOUNT', '1.0', '/legal/privacy#account',
     'We store your email address and a hash of your password so you can sign in.',
     '2026-09-05T00:00:00Z'),
    ('PERSONAL_FIT', '1.0', '/legal/privacy#personal-fit',
     'We use the dietary goals, allergens and preferences you enter to assess how a '
     'product fits you. You can withdraw this at any time; assessments stop immediately.',
     '2026-09-05T00:00:00Z'),
    ('CHILD_FOOD_SUITABILITY', '1.0', '/legal/privacy#child',
     'A guardian may add a child profile holding an age band, allergens and dietary '
     'preferences, used only to assess whether a product suits that child.',
     '2026-09-05T00:00:00Z'),
    ('LABEL_IMAGE_PROCESSING', '1.0', '/legal/privacy#label-images',
     'We process label photographs to extract nutrition values, and delete the image '
     'after 90 days.',
     '2026-09-05T00:00:00Z'),
    ('PRODUCT_IMPROVEMENT', '1.0', '/legal/privacy#contribution',
     'Product information you confirm may be reviewed and added to our shared catalogue.',
     '2026-09-05T00:00:00Z');


-- -----------------------------------------------------------------------------
-- Categories
--
-- typical_serving_min_g / max_g drive the "implausible declared serving" flag
-- (D-010). Indian pack servings are frequently unrealistically small, and a
-- per-serving verdict alone would be manufacturer-gameable.
-- -----------------------------------------------------------------------------
CREATE TABLE category (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id             uuid REFERENCES category(id),
    code                  text NOT NULL UNIQUE,
    name                  text NOT NULL,
    path                  text NOT NULL,
    typical_serving_min_g numeric(8,2),
    typical_serving_max_g numeric(8,2),
    sort_order            int NOT NULL DEFAULT 0
);

CREATE INDEX ix_category_parent ON category (parent_id);

INSERT INTO category (code, name, path, typical_serving_min_g, typical_serving_max_g, sort_order) VALUES
    ('BISCUITS',        'Biscuits and cookies',      '/biscuits',        25,  50,  10),
    ('BREAKFAST_CEREAL','Breakfast cereal and oats', '/breakfast-cereal',30,  60,  20),
    ('NAMKEEN',         'Namkeen and salty snacks',  '/namkeen',         25,  50,  30),
    ('BEVERAGES',       'Packaged beverages',        '/beverages',      200, 300,  40),
    ('MALT_DRINK',      'Malt and health drinks',    '/malt-drink',      20,  35,  50),
    ('STAPLES',         'Atta, bread and staples',   '/staples',         30,  60,  60),
    ('DAIRY',           'Dairy',                     '/dairy',          100, 200,  70),
    ('EDIBLE_OIL',      'Edible oils and ghee',      '/edible-oil',       5,  15,  80),
    ('CHOCOLATE',       'Chocolate and confectionery','/chocolate',      20,  50,  90),
    ('INSTANT_NOODLES', 'Instant noodles and pasta', '/instant-noodles', 60, 100, 100),
    ('SAUCES_SPREADS',  'Sauces, spreads, condiments','/sauces-spreads',  10,  30, 110),
    ('PROTEIN_FOODS',   'Protein and health foods',  '/protein-foods',   25,  50, 120),
    ('CHIPS',           'Chips and wafers',          '/chips',           25,  40, 130);
