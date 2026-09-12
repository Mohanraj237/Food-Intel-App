-- Phase 2 of the wellness scope (D-034): nutrition logging with family apportioning
-- (D-039), goals, and habits.
--
-- The organising idea is that a meal is a *composition event* that produces
-- per-member nutrition, rather than a per-member food diary duplicated N times.
-- One dinner cooked for four people is one `meal` with four `meal_portion`
-- rows — which is what makes "log it once, apportion it" a data shape rather
-- than a UI trick, and what stops a correction to the recipe requiring four
-- separate edits.

-- =============================================================== observations

-- Lets a derived observation point back at what produced it, so nutrition can
-- recompute its own rows without wellness needing to know what a meal is.
--
-- Observations are append-only: editing a meal does not mutate an observation,
-- it deletes the rows derived from that meal and writes fresh ones. Without
-- this column that would be impossible to do precisely, and a corrected meal
-- would silently double-count.
ALTER TABLE health_observation
    ADD COLUMN source_ref uuid;

COMMENT ON COLUMN health_observation.source_ref IS
    'Optional id of the thing that produced a DERIVED observation (today: a meal). Opaque to wellness — the producing module owns the meaning. Enables delete-and-rewrite on edit without mutating rows.';

CREATE INDEX ix_observation_source_ref
    ON health_observation (source_ref)
    WHERE source_ref IS NOT NULL;

-- ===================================================================== meals

CREATE TABLE meal (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Scoped to a household, not to one member: that is the whole point. An
    -- Individual Mode meal is a meal in a household of one (D-035), so there is
    -- no second code path for "just me".
    household_id    uuid NOT NULL REFERENCES household(id) ON DELETE CASCADE,

    meal_type       text NOT NULL CHECK (meal_type IN ('BREAKFAST','LUNCH','DINNER','SNACK')),

    -- Optional: "Sunday rajma chawal". Useful for reuse later, never required.
    name            text CHECK (name IS NULL OR length(btrim(name)) BETWEEN 1 AND 120),

    consumed_at     timestamptz NOT NULL,

    created_by_user_id uuid NOT NULL REFERENCES user_account(id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),

    -- A meal cannot be eaten in the future.
    CONSTRAINT ck_meal_not_future CHECK (consumed_at <= created_at + interval '1 day')
);

CREATE INDEX ix_meal_household_consumed ON meal (household_id, consumed_at DESC);

COMMENT ON TABLE meal IS
    'One eating occasion for a household (D-039). Apportioned to members through meal_portion; per-member nutrition is derived into health_observation.';

-- --------------------------------------------------------------- meal items

CREATE TABLE meal_item (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_id     uuid NOT NULL REFERENCES meal(id) ON DELETE CASCADE,

    -- Two ways to say what was eaten, exactly one of which must be present.
    --
    -- A catalogue item carries the product *version* rather than the product, so
    -- a log says what the label said on the day it was eaten — the same
    -- versioning discipline every verdict already follows (D-014). A
    -- reformulation next year must not silently rewrite last year's dinner.
    product_id          uuid REFERENCES product(id),
    product_version_id  uuid REFERENCES product_version(id),
    custom_name         text CHECK (custom_name IS NULL OR length(btrim(custom_name)) BETWEEN 1 AND 120),

    quantity_g  numeric(10,2) NOT NULL CHECK (quantity_g > 0),

    -- The nutrition this item contributed, as computed when it was logged.
    --
    -- Denormalised on purpose. This is a historical log, and an invoice stores
    -- the price at the time of sale rather than joining to today's price list.
    -- Recomputing from product_nutrient on every read would make "what I ate on
    -- Tuesday" change when the catalogue is corrected, which is exactly the
    -- behaviour a food log must not have. The version reference above keeps the
    -- provenance recoverable.
    energy_kcal numeric(10,2),
    protein_g   numeric(10,2),
    carbohydrate_g numeric(10,2),
    fat_g       numeric(10,2),
    fibre_g     numeric(10,2),
    sugar_g     numeric(10,2),
    sodium_mg   numeric(10,2),

    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_meal_item_identifies_food CHECK (
        (product_id IS NOT NULL AND custom_name IS NULL)
        OR (product_id IS NULL AND custom_name IS NOT NULL)),

    -- A catalogue item must name the version it was read from.
    CONSTRAINT ck_meal_item_version_with_product CHECK (
        product_id IS NULL OR product_version_id IS NOT NULL),

    -- Nutrition may be absent entirely (a home-cooked item nobody has values
    -- for), but a negative amount is never meaningful.
    CONSTRAINT ck_meal_item_non_negative CHECK (
        coalesce(energy_kcal, 0) >= 0 AND coalesce(protein_g, 0) >= 0
        AND coalesce(carbohydrate_g, 0) >= 0 AND coalesce(fat_g, 0) >= 0
        AND coalesce(fibre_g, 0) >= 0 AND coalesce(sugar_g, 0) >= 0
        AND coalesce(sodium_mg, 0) >= 0)
);

CREATE INDEX ix_meal_item_meal ON meal_item (meal_id);

-- ------------------------------------------------------------ apportioning

CREATE TABLE meal_portion (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_id             uuid NOT NULL REFERENCES meal(id) ON DELETE CASCADE,
    household_member_id uuid NOT NULL REFERENCES household_member(id) ON DELETE CASCADE,

    -- What share of the whole meal this person ate. A fraction rather than a
    -- weight because that is how people actually describe it ("he had about
    -- half of what I had"), and because it stays correct if an item's quantity
    -- is later corrected — the split does not need re-entering.
    --
    -- Not constrained to sum to 1 across a meal: a family rarely eats exactly
    -- all of what was cooked, and forcing the sum would make leftovers
    -- unrepresentable or push users into lying about portions.
    portion_fraction    numeric(6,4) NOT NULL
                            CHECK (portion_fraction > 0 AND portion_fraction <= 10),

    created_at          timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_meal_portion_member UNIQUE (meal_id, household_member_id)
);

CREATE INDEX ix_meal_portion_member ON meal_portion (household_member_id);

COMMENT ON COLUMN meal_portion.portion_fraction IS
    'Share of the whole meal, e.g. 1.0 = all of it, 0.5 = half. Deliberately not required to sum to 1 across a meal — leftovers and uneven servings are normal.';

-- ===================================================================== goals

CREATE TABLE wellness_goal (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    household_member_id uuid NOT NULL REFERENCES household_member(id) ON DELETE CASCADE,

    -- The metric this goal is about, from the same closed taxonomy observations
    -- use. This is what makes the D-036 age gate apply to goals for free: a
    -- WEIGHT_KG goal for a child is refused by exactly the code that refuses a
    -- WEIGHT_KG observation, with no second safety implementation to keep in
    -- step. Not a foreign key — MetricCode is a code-side enum, and the column
    -- is validated against it in the domain.
    metric_code         text NOT NULL,

    direction           text NOT NULL CHECK (direction IN ('AT_LEAST','AT_MOST','MAINTAIN')),
    target_value        numeric(12,3) NOT NULL CHECK (target_value >= 0),

    -- The window the target applies to. Daily for intake-style goals, weekly for
    -- accumulating ones.
    period              text NOT NULL CHECK (period IN ('DAILY','WEEKLY')),

    started_on          date NOT NULL,
    ended_on            date,
    is_active           boolean NOT NULL DEFAULT true,

    created_by_user_id  uuid NOT NULL REFERENCES user_account(id),
    created_at          timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_goal_ended_after_started CHECK (ended_on IS NULL OR ended_on >= started_on),
    CONSTRAINT ck_goal_inactive_has_end CHECK (is_active OR ended_on IS NOT NULL)
);

-- One live goal per (member, metric). Changing a target ends the old goal and
-- starts a new one, so progress history stays attributable to the target that
-- was actually in force. A partial index rather than a UNIQUE with a nullable
-- column, for the same NULL-distinctness reason as metric_visibility in V12.
CREATE UNIQUE INDEX uq_goal_live_per_metric
    ON wellness_goal (household_member_id, metric_code)
    WHERE is_active;

CREATE INDEX ix_goal_member ON wellness_goal (household_member_id, is_active);

-- ==================================================================== habits

CREATE TABLE habit (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    household_member_id uuid NOT NULL REFERENCES household_member(id) ON DELETE CASCADE,

    name                text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 80),

    -- How often it is meant to happen. Kept minimal: enough to say "most days"
    -- without becoming a scheduling engine.
    cadence             text NOT NULL CHECK (cadence IN ('DAILY','WEEKDAYS','WEEKLY')),

    is_active           boolean NOT NULL DEFAULT true,
    created_by_user_id  uuid NOT NULL REFERENCES user_account(id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    archived_at         timestamptz,

    CONSTRAINT ck_habit_inactive_is_archived CHECK (is_active OR archived_at IS NOT NULL)
);

CREATE INDEX ix_habit_member ON habit (household_member_id, is_active);

-- Deliberately no streak, badge, points or rank column anywhere in this file.
-- Consistency is reported as "5 of the last 7 days" computed at read time, which
-- is a fact about the log; a stored streak is a score, and scores aimed at a
-- child are a permanent boundary (D-036, CH-004, post-mvp-backlog).
CREATE TABLE habit_completion (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    habit_id        uuid NOT NULL REFERENCES habit(id) ON DELETE CASCADE,

    -- A date, not a timestamp: "did this happen today" is a calendar question,
    -- and a timestamp would make the same day ambiguous across time zones.
    completed_on    date NOT NULL,

    recorded_by_user_id uuid NOT NULL REFERENCES user_account(id),
    recorded_at     timestamptz NOT NULL DEFAULT now(),

    -- Completing twice in a day is the same fact stated twice, not two facts.
    CONSTRAINT uq_habit_completion_day UNIQUE (habit_id, completed_on)
);

CREATE INDEX ix_habit_completion_habit_day ON habit_completion (habit_id, completed_on DESC);
