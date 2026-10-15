-- =====================================================================================
-- Demo data: one week in the life of a three-generation household (the Iyer family).
--
-- LOCAL DEVELOPMENT ONLY. Deliberately NOT a Flyway migration: demo people must never
-- ride the production migration chain. Run it by hand against a local database.
--
--   docker exec -i ffi-postgres psql -U familyfood -d familyfood -v ON_ERROR_STOP=1 \
--     < tools/demo-seed/demo-family.sql
--
-- Re-runnable: run tools/demo-seed/reset-demo-family.sql first, which removes exactly
-- the accounts using the reserved `.demo@example.com` suffix and the household they own.
--
-- WHAT THIS RESPECTS (the rules live in the application layer, so SQL must honour them
-- by hand or it will produce a database the app itself would have refused to create):
--   * D-036 age gate      — no BODY or CLINICAL metric is ever recorded for a child.
--   * D-047 child goals   — children get ACTIVITY/SLEEP/HYDRATION goals only, and only
--                           AT_LEAST or MAINTAIN. No calorie, weight or "at most" goal.
--   * D-041 meal portions — another adult's share stays out of their log (derived_at
--                           NULL, no derived observations) until they confirm it.
--   * D-044 sharing       — only a subject grants, never about a child, offered scopes only.
--   * D-048 consent       — every tracked member has an ACTIVE consent row; children are
--                           guardian-attested.
--   * D-053 day boundary  — every timestamp is built as Asia/Kolkata wall-clock.
--
-- CREDENTIALS: all eight adults share the password  DemoFamily2026!seed
-- The hash below is a real Argon2id digest of that password, produced by the application's
-- own encoder (m=19456,t=2,p=1). The same digest is reused for every demo account on
-- purpose: it is one known, obviously-fake local password, and generating eight distinct
-- salts buys nothing when the plaintext is published in this comment. Never reuse any of
-- this anywhere that is deployed.
-- =====================================================================================

BEGIN;

-- ------------------------------------------------------------------ 0. identifiers
-- Fixed UUIDs so every later statement can reference a row without RETURNING plumbing,
-- and so a re-run after the reset script produces byte-identical ids.

CREATE TEMP TABLE ids (k text PRIMARY KEY, v uuid);
INSERT INTO ids (k, v) VALUES
  ('household',   'd0de0000-0000-4000-8000-000000000001'),
  -- accounts (adults only; a child never has one)
  ('acc_krishnan','a0de0000-0000-4000-8000-000000000001'),
  ('acc_lakshmi', 'a0de0000-0000-4000-8000-000000000002'),
  ('acc_raghav',  'a0de0000-0000-4000-8000-000000000003'),
  ('acc_meera',   'a0de0000-0000-4000-8000-000000000004'),
  ('acc_arjun',   'a0de0000-0000-4000-8000-000000000005'),
  ('acc_vikram',  'a0de0000-0000-4000-8000-000000000006'),
  ('acc_divya',   'a0de0000-0000-4000-8000-000000000007'),
  ('acc_kavya',   'a0de0000-0000-4000-8000-000000000008'),
  -- members
  ('mem_krishnan','b0de0000-0000-4000-8000-000000000001'),
  ('mem_lakshmi', 'b0de0000-0000-4000-8000-000000000002'),
  ('mem_raghav',  'b0de0000-0000-4000-8000-000000000003'),
  ('mem_meera',   'b0de0000-0000-4000-8000-000000000004'),
  ('mem_arjun',   'b0de0000-0000-4000-8000-000000000005'),
  ('mem_vikram',  'b0de0000-0000-4000-8000-000000000006'),
  ('mem_divya',   'b0de0000-0000-4000-8000-000000000007'),
  ('mem_kavya',   'b0de0000-0000-4000-8000-000000000008'),
  ('mem_aarav',   'b0de0000-0000-4000-8000-000000000009'),
  ('mem_ananya',  'b0de0000-0000-4000-8000-00000000000a');

CREATE OR REPLACE FUNCTION pg_temp.id(text) RETURNS uuid
  LANGUAGE sql STABLE AS $$ SELECT v FROM ids WHERE k = $1 $$;

-- Today, in the household's own zone — not the server's.
CREATE OR REPLACE FUNCTION pg_temp.ist_today() RETURNS date
  LANGUAGE sql STABLE AS $$ SELECT (now() AT TIME ZONE 'Asia/Kolkata')::date $$;

-- `days_ago` + a wall-clock time, interpreted as Asia/Kolkata, returned as an instant.
CREATE OR REPLACE FUNCTION pg_temp.ist_at(days_ago int, at_time time) RETURNS timestamptz
  LANGUAGE sql STABLE AS
$$ SELECT ((pg_temp.ist_today() - days_ago)::timestamp + at_time) AT TIME ZONE 'Asia/Kolkata' $$;

-- ------------------------------------------------------------------ 1. accounts
INSERT INTO user_account (id, email, password_hash, status, role)
SELECT pg_temp.id(k), email,
       '$argon2id$v=19$m=19456,t=2,p=1$qbAhVPgrSPUgvMQnuWD+5g$rOH0ovFM+sdbhLTP+Xywi4E3PHN7diVJHDXetg/vDDo',
       'ACTIVE', 'MEMBER'
FROM (VALUES
  ('acc_krishnan','krishnan.iyer.demo@example.com'),
  ('acc_lakshmi', 'lakshmi.iyer.demo@example.com'),
  ('acc_raghav',  'raghav.iyer.demo@example.com'),
  ('acc_meera',   'meera.iyer.demo@example.com'),
  ('acc_arjun',   'arjun.iyer.demo@example.com'),
  ('acc_vikram',  'vikram.iyer.demo@example.com'),
  ('acc_divya',   'divya.iyer.demo@example.com'),
  ('acc_kavya',   'kavya.iyer.demo@example.com')
) AS a(k, email);

-- ------------------------------------------------------------------ 2. household
INSERT INTO household (id, name, created_by_user_id, is_individual, time_zone)
VALUES (pg_temp.id('household'), 'Iyer family', pg_temp.id('acc_raghav'), false, 'Asia/Kolkata');

-- Members. A CHILD must have no account and role DEPENDENT; an ADULT the exact inverse
-- (both are equivalence CHECKs, not one-way rules).
INSERT INTO household_member (id, household_id, user_account_id, member_type, role, display_label, joined_at)
SELECT pg_temp.id(mk), pg_temp.id('household'),
       CASE WHEN ak IS NULL THEN NULL ELSE pg_temp.id(ak) END,
       mtype, mrole, label, now() - (offset_days || ' days')::interval
FROM (VALUES
  ('mem_raghav',  'acc_raghav',  'ADULT', 'OWNER',     'Raghav',   400),
  ('mem_meera',   'acc_meera',   'ADULT', 'ADULT',     'Meera',    399),
  ('mem_krishnan','acc_krishnan','ADULT', 'ADULT',     'Krishnan', 398),
  ('mem_lakshmi', 'acc_lakshmi', 'ADULT', 'ADULT',     'Lakshmi',  397),
  ('mem_arjun',   'acc_arjun',   'ADULT', 'ADULT',     'Arjun',    396),
  ('mem_vikram',  'acc_vikram',  'ADULT', 'ADULT',     'Vikram',   395),
  ('mem_divya',   'acc_divya',   'ADULT', 'ADULT',     'Divya',    394),
  ('mem_kavya',   'acc_kavya',   'ADULT', 'ADULT',     'Kavya',    393),
  ('mem_aarav',   NULL,          'CHILD', 'DEPENDENT', 'Aarav',    392),
  ('mem_ananya',  NULL,          'CHILD', 'DEPENDENT', 'Ananya',   391)
) AS m(mk, ak, mtype, mrole, label, offset_days);

-- Guardianship. Deliberately split across the two parents so the product's "a child you
-- do NOT look after" state has a real person behind it: Raghav guards Aarav, Meera guards
-- Ananya, and each parent sees the other's ward as identity-only.
INSERT INTO guardian_link (child_member_id, guardian_member_id, relationship, authority_basis)
VALUES
  (pg_temp.id('mem_aarav'),  pg_temp.id('mem_raghav'), 'PARENT', 'Parent — demo data, guardian attested at seed time'),
  (pg_temp.id('mem_ananya'), pg_temp.id('mem_meera'),  'PARENT', 'Parent — demo data, guardian attested at seed time');

-- ------------------------------------------------------------------ 3. profiles
INSERT INTO person_profile (id, household_member_id, version, is_current, age_band, sex,
                            dietary_preference, activity_level, created_by_user_id)
SELECT ('c0de0000-0000-4000-8000-0000000000' || lpad(rn::text, 2, '0'))::uuid,
       pg_temp.id(mk), 1, true, band, sx, diet, act, pg_temp.id(creator)
FROM (VALUES
  -- Grandparents: older-adult tier.
  (1, 'mem_krishnan','A60_PLUS','MALE',  'VEG',        'SEDENTARY',          'acc_krishnan'),
  (2, 'mem_lakshmi', 'A60_PLUS','FEMALE','VEG',        'LIGHTLY_ACTIVE',     'acc_lakshmi'),
  -- Parents.
  (3, 'mem_raghav',  'A31_45',  'MALE',  'VEG',        'MODERATELY_ACTIVE',  'acc_raghav'),
  (4, 'mem_meera',   'A31_45',  'FEMALE','VEG',        'LIGHTLY_ACTIVE',     'acc_meera'),
  -- Brothers and sisters sharing the household.
  (5, 'mem_arjun',   'A18_30',  'MALE',  'EGGETARIAN', 'VERY_ACTIVE',        'acc_arjun'),
  (6, 'mem_vikram',  'A31_45',  'MALE',  'NON_VEG',    'MODERATELY_ACTIVE',  'acc_vikram'),
  (7, 'mem_divya',   'A18_30',  'FEMALE','VEG',        'MODERATELY_ACTIVE',  'acc_divya'),
  (8, 'mem_kavya',   'A31_45',  'FEMALE','VEG',        'LIGHTLY_ACTIVE',     'acc_kavya'),
  -- Children. activity_level is deliberately left NULL: it has no child use that does not
  -- drift towards an energy target (D-047). Each is created by their own guardian.
  (9, 'mem_aarav',   'C7_9',    'MALE',  'VEG',        NULL,                 'acc_raghav'),
  (10,'mem_ananya',  'C10_12',  'FEMALE','VEG',        NULL,                 'acc_meera')
) AS p(rn, mk, band, sx, diet, act, creator);

INSERT INTO profile_goal (profile_id, goal_code)
SELECT pp.id, g.code
FROM person_profile pp
JOIN household_member hm ON hm.id = pp.household_member_id AND hm.household_id = pg_temp.id('household')
JOIN (VALUES
  ('mem_krishnan','REDUCE_ADDED_SUGAR'), ('mem_krishnan','REDUCE_SODIUM'),
  ('mem_lakshmi', 'GENERAL_BALANCED_EATING'),
  ('mem_raghav',  'REDUCE_SODIUM'),      ('mem_raghav','INCREASE_FIBRE'),
  ('mem_meera',   'INCREASE_PROTEIN'),
  ('mem_arjun',   'INCREASE_PROTEIN'),
  ('mem_vikram',  'REDUCE_SATURATED_FAT'),
  ('mem_divya',   'GENERAL_BALANCED_EATING'),
  ('mem_kavya',   'INCREASE_FIBRE')
) AS g(mk, code) ON pp.household_member_id = pg_temp.id(g.mk);

INSERT INTO profile_allergen (profile_id, allergen_code)
SELECT pp.id, a.code
FROM person_profile pp
JOIN (VALUES
  ('mem_meera','PEANUT'), ('mem_lakshmi','MILK'), ('mem_divya','TREE_NUT'), ('mem_aarav','MILK')
) AS a(mk, code) ON pp.household_member_id = pg_temp.id(a.mk);

-- Free text by design, and never shared even with a caregiver (FOOD_PROFILE excludes conditions).
INSERT INTO profile_condition (profile_id, condition_code, self_declared)
SELECT pp.id, c.code, true
FROM person_profile pp
JOIN (VALUES
  ('mem_krishnan','Type 2 diabetes'), ('mem_lakshmi','High blood pressure')
) AS c(mk, code) ON pp.household_member_id = pg_temp.id(c.mk);

-- ------------------------------------------------------------------ 4. consent
-- Adults self-attest; children are guardian-attested (the CHECK enforces the mechanism).
INSERT INTO consent_record (subject_member_id, subject_type, granted_by_user_id, purpose_code,
                            policy_version_id, status, mechanism, evidence, granted_at)
SELECT pg_temp.id(c.mk), c.stype, pg_temp.id(c.granter), c.purpose,
       (SELECT id FROM consent_policy_version WHERE purpose_code = c.purpose AND effective_to IS NULL),
       'ACTIVE', c.mech,
       jsonb_build_object('source', 'demo-seed'), now() - interval '30 days'
FROM (
  SELECT mk, granter, 'ADULT_SELF' AS stype, 'SELF_ATTESTATION' AS mech, p AS purpose
  FROM (VALUES
    ('mem_krishnan','acc_krishnan'), ('mem_lakshmi','acc_lakshmi'),
    ('mem_raghav','acc_raghav'),     ('mem_meera','acc_meera'),
    ('mem_arjun','acc_arjun'),       ('mem_vikram','acc_vikram'),
    ('mem_divya','acc_divya'),       ('mem_kavya','acc_kavya')
  ) AS adults(mk, granter)
  CROSS JOIN (VALUES ('ACCOUNT'), ('PERSONAL_FIT'), ('HEALTH_TRACKING')) AS purposes(p)
  UNION ALL
  SELECT mk, granter, 'CHILD', 'GUARDIAN_ATTESTATION', p
  FROM (VALUES ('mem_aarav','acc_raghav'), ('mem_ananya','acc_meera')) AS kids(mk, granter)
  CROSS JOIN (VALUES ('CHILD_FOOD_SUITABILITY'), ('CHILD_WELLNESS_TRACKING')) AS purposes(p)
) AS c;

-- ------------------------------------------------------------------ 5. a week of observations
-- One row per (member, metric, day). `gap_day` carves a deliberate hole in the week so the
-- product's "Not logged" state is exercised rather than every day being suspiciously complete.
--
-- NOTE the age gate: BODY and CLINICAL metrics appear only for adult members. Adding a
-- WEIGHT_KG or SYSTOLIC_MMHG row for Aarav or Ananya here would create data the application
-- would have refused (D-036) and the UI has no screen for.

CREATE TEMP TABLE obs_plan (
  mk text, metric text, category text, unit text,
  base numeric, step numeric, at_time time, gap_day int
);

INSERT INTO obs_plan VALUES
  -- Father — a full picture, with one missed water day.
  ('mem_raghav','STEPS','ACTIVITY','steps',          7200, 340, '21:10', NULL),
  ('mem_raghav','SLEEP_MINUTES','SLEEP','min',        400,  12, '07:00', NULL),
  ('mem_raghav','WATER_ML','HYDRATION','ml',         2200, 120, '20:00', 4),
  ('mem_raghav','WEIGHT_KG','BODY','kg',             78.4,-0.1, '07:15', NULL),
  ('mem_raghav','MOOD_SCORE','WELLBEING','score',       4,   0, '22:00', 3),
  -- Mother.
  ('mem_meera','STEPS','ACTIVITY','steps',           6100, 210, '21:00', NULL),
  ('mem_meera','SLEEP_MINUTES','SLEEP','min',         380,  15, '06:40', NULL),
  ('mem_meera','WATER_ML','HYDRATION','ml',          1800, 150, '20:30', NULL),
  ('mem_meera','WEIGHT_KG','BODY','kg',              61.2, 0.05,'07:05', NULL),
  ('mem_meera','MOOD_SCORE','WELLBEING','score',        4,   0, '22:10', NULL),
  -- Grandfather — low activity, clinical readings, a diabetes-relevant glucose series.
  ('mem_krishnan','STEPS','ACTIVITY','steps',        2400, 120, '19:00', NULL),
  ('mem_krishnan','SLEEP_MINUTES','SLEEP','min',      330,  18, '06:00', NULL),
  ('mem_krishnan','WATER_ML','HYDRATION','ml',       1400, 100, '19:30', 5),
  ('mem_krishnan','ACTIVE_MINUTES','ACTIVITY','min',   15,   3, '08:00', NULL),
  ('mem_krishnan','SYSTOLIC_MMHG','CLINICAL','mmHg',  132,   1, '08:05', NULL),
  ('mem_krishnan','DIASTOLIC_MMHG','CLINICAL','mmHg',  84,   1, '08:05', NULL),
  ('mem_krishnan','RESTING_HEART_RATE_BPM','CLINICAL','bpm', 72, 1, '08:06', NULL),
  ('mem_krishnan','BLOOD_GLUCOSE_MGDL','CLINICAL','mg/dL',  126, 2, '07:30', 2),
  -- Grandmother.
  ('mem_lakshmi','STEPS','ACTIVITY','steps',         3600, 150, '19:15', NULL),
  ('mem_lakshmi','SLEEP_MINUTES','SLEEP','min',       360,  14, '06:10', NULL),
  ('mem_lakshmi','WATER_ML','HYDRATION','ml',        1500, 110, '19:45', NULL),
  ('mem_lakshmi','SYSTOLIC_MMHG','CLINICAL','mmHg',   128,   1, '08:20', NULL),
  ('mem_lakshmi','DIASTOLIC_MMHG','CLINICAL','mmHg',   80,   1, '08:20', NULL),
  -- Brother Arjun — the athlete.
  ('mem_arjun','STEPS','ACTIVITY','steps',          11000, 420, '22:00', NULL),
  ('mem_arjun','SLEEP_MINUTES','SLEEP','min',         420,  16, '07:30', NULL),
  ('mem_arjun','WATER_ML','HYDRATION','ml',          3100, 180, '21:00', NULL),
  ('mem_arjun','WORKOUT_MINUTES','ACTIVITY','min',     45,   6, '18:30', 2),
  ('mem_arjun','DISTANCE_KM','ACTIVITY','km',         6.2, 0.6, '18:30', 2),
  -- Brother Vikram — an ordinary desk week.
  ('mem_vikram','STEPS','ACTIVITY','steps',          5400, 260, '20:45', NULL),
  ('mem_vikram','SLEEP_MINUTES','SLEEP','min',        395,  11, '07:20', NULL),
  ('mem_vikram','WATER_ML','HYDRATION','ml',         2000, 140, '20:10', 1),
  ('mem_vikram','WEIGHT_KG','BODY','kg',             83.1,-0.08,'07:25', NULL),
  -- Sister Divya.
  ('mem_divya','STEPS','ACTIVITY','steps',           8300, 260, '21:30', NULL),
  ('mem_divya','SLEEP_MINUTES','SLEEP','min',         405,  13, '07:10', NULL),
  ('mem_divya','WATER_ML','HYDRATION','ml',          2400, 130, '20:15', NULL),
  ('mem_divya','MOOD_SCORE','WELLBEING','score',         4,  0, '22:30', NULL),
  -- Sister Kavya.
  ('mem_kavya','STEPS','ACTIVITY','steps',           6800, 190, '21:05', NULL),
  ('mem_kavya','SLEEP_MINUTES','SLEEP','min',         410,  10, '06:55', NULL),
  ('mem_kavya','WATER_ML','HYDRATION','ml',          2100, 120, '20:20', NULL),
  ('mem_kavya','ACTIVE_MINUTES','ACTIVITY','min',      28,   4, '18:00', 3),
  -- Son (7-9) — guardian-entered, no body or clinical metric, ever.
  ('mem_aarav','STEPS','ACTIVITY','steps',           7400, 200, '19:00', NULL),
  ('mem_aarav','SLEEP_MINUTES','SLEEP','min',         560,   9, '07:00', NULL),
  ('mem_aarav','WATER_ML','HYDRATION','ml',          1100,  90, '18:00', NULL),
  ('mem_aarav','MOOD_SCORE','WELLBEING','score',         4,  0, '20:00', 2),
  -- Daughter (10-12).
  ('mem_ananya','STEPS','ACTIVITY','steps',          8600, 180, '19:30', NULL),
  ('mem_ananya','SLEEP_MINUTES','SLEEP','min',        530,  11, '06:50', NULL),
  ('mem_ananya','WATER_ML','HYDRATION','ml',         1250, 100, '18:30', 3),
  ('mem_ananya','MOOD_SCORE','WELLBEING','score',        5,  0, '20:30', NULL);

-- A child's data is recorded by their guardian; an adult records their own.
CREATE TEMP TABLE recorder (mk text PRIMARY KEY, ak text);
INSERT INTO recorder VALUES
  ('mem_krishnan','acc_krishnan'), ('mem_lakshmi','acc_lakshmi'),
  ('mem_raghav','acc_raghav'),     ('mem_meera','acc_meera'),
  ('mem_arjun','acc_arjun'),       ('mem_vikram','acc_vikram'),
  ('mem_divya','acc_divya'),       ('mem_kavya','acc_kavya'),
  ('mem_aarav','acc_raghav'),      ('mem_ananya','acc_meera');

INSERT INTO health_observation (household_member_id, metric_code, metric_category, value, unit,
                                effective_at, recorded_at, recorded_by_user_id, source)
SELECT pg_temp.id(p.mk), p.metric, p.category,
       round(p.base + p.step * d, 3), p.unit,
       pg_temp.ist_at(d, p.at_time),
       now(), pg_temp.id(r.ak), 'MANUAL'
FROM obs_plan p
JOIN recorder r ON r.mk = p.mk
CROSS JOIN generate_series(0, 6) AS d
WHERE p.gap_day IS NULL OR p.gap_day <> d;

-- ------------------------------------------------------------------ 6. meals and portions
-- Two meals a day for seven days. Nutrition columns on meal_item are whole-item totals
-- (already scaled for quantity_g) — there are no per-100 columns in the schema.

INSERT INTO meal (id, household_id, meal_type, name, consumed_at, created_by_user_id)
SELECT ('e0de0000-0000-4000-8000-0000000000' || lpad((d * 2 + n)::text, 2, '0'))::uuid,
       pg_temp.id('household'), m.mtype, m.mname,
       pg_temp.ist_at(d, m.at_time), pg_temp.id(m.logger)
FROM generate_series(0, 6) AS d
CROSS JOIN (VALUES
  (0, 'BREAKFAST', 'Idli and sambar', TIME '08:15', 'acc_meera'),
  (1, 'DINNER',    'Family dinner',   TIME '20:30', 'acc_raghav')
) AS m(n, mtype, mname, at_time, logger);

INSERT INTO meal_item (meal_id, custom_name, quantity_g, energy_kcal, protein_g, carbohydrate_g,
                       fat_g, fibre_g, sugar_g, sodium_mg)
SELECT ml.id, i.nm, i.qty, i.kcal, i.prot, i.carb, i.fat, i.fib, i.sug, i.sod
FROM meal ml
JOIN (VALUES
  ('BREAKFAST','Idli',       180, 266.4,  7.9, 54.0,  1.1, 2.5, 0.9, 378),
  ('BREAKFAST','Sambar',     200, 170.0,  7.6, 24.0,  4.8, 6.2, 4.4, 840),
  ('DINNER',   'Dal tadka',  220, 259.6, 13.6, 30.8,  9.0, 8.8, 3.5, 836),
  ('DINNER',   'Jeera rice', 260, 429.0,  8.8, 85.8,  6.0, 2.9, 0.8, 494)
) AS i(mt, nm, qty, kcal, prot, carb, fat, fib, sug, sod) ON i.mt = ml.meal_type
WHERE ml.household_id = pg_temp.id('household');

-- Portions. `derived_at` is the D-041 switch: set means it is in that member's own log;
-- NULL means the meal records it but the member has not confirmed it yet.
--   * The logger's own share, and a child's share logged by their guardian, derive at once.
--   * Meera's dinner share is confirmed on the older days and left waiting on the two most
--     recent, so "Waiting for you" is visible in the running app.
--   * Krishnan never confirms: his share stays pending all week, which is what an elder's
--     log looks like before a caregiver helps.
INSERT INTO meal_portion (meal_id, household_member_id, portion_fraction, derived_at)
SELECT ml.id, pg_temp.id(p.mk), p.frac,
       CASE
         WHEN p.confirm_rule = 'always' THEN ml.consumed_at
         WHEN p.confirm_rule = 'older_days_only'
              AND ml.consumed_at < pg_temp.ist_at(1, TIME '00:00') THEN ml.consumed_at
         ELSE NULL
       END
FROM meal ml
JOIN (VALUES
  -- Breakfast: logged by Meera for herself and the daughter she guards.
  ('BREAKFAST','mem_meera',  0.5,  'always'),
  ('BREAKFAST','mem_ananya', 0.5,  'always'),
  -- Dinner: logged by Raghav — himself, the son he guards, plus two other adults.
  ('DINNER',   'mem_raghav',  0.5, 'always'),
  ('DINNER',   'mem_aarav',   0.25,'always'),
  ('DINNER',   'mem_meera',   0.5, 'older_days_only'),
  ('DINNER',   'mem_krishnan',0.5, 'never'),
  ('DINNER',   'mem_vikram',  0.5, 'never')
) AS p(mt, mk, frac, confirm_rule) ON p.mt = ml.meal_type
WHERE ml.household_id = pg_temp.id('household');

-- Derived nutrition: exactly the portions that are in a member's log, and nothing else.
-- value = (sum of the meal's item totals) x portion_fraction, attributed to the meal via
-- source_ref so the number on a tracker screen can be traced back to what was eaten.
INSERT INTO health_observation (household_member_id, metric_code, metric_category, value, unit,
                                effective_at, recorded_at, recorded_by_user_id, source, source_ref)
SELECT t.household_member_id, x.code, 'NUTRITION', round(x.total * t.portion_fraction, 3), x.unit,
       t.consumed_at, now(), t.created_by_user_id, 'DERIVED', t.meal_id
FROM (
  SELECT mp.meal_id, mp.household_member_id, mp.portion_fraction,
         ml.consumed_at, ml.created_by_user_id,
         sum(mi.energy_kcal)    AS e, sum(mi.protein_g) AS p, sum(mi.carbohydrate_g) AS c,
         sum(mi.fat_g)          AS f, sum(mi.fibre_g)   AS fb,
         sum(mi.sugar_g)        AS s, sum(mi.sodium_mg) AS na
  FROM meal_portion mp
  JOIN meal ml      ON ml.id = mp.meal_id
  JOIN meal_item mi ON mi.meal_id = ml.id
  WHERE ml.household_id = pg_temp.id('household')
    AND mp.derived_at IS NOT NULL
  GROUP BY mp.meal_id, mp.household_member_id, mp.portion_fraction, ml.consumed_at, ml.created_by_user_id
) AS t
CROSS JOIN LATERAL (VALUES
  ('ENERGY_KCAL', t.e, 'kcal'), ('PROTEIN_G', t.p, 'g'), ('CARBOHYDRATE_G', t.c, 'g'),
  ('FAT_G', t.f, 'g'), ('FIBRE_G', t.fb, 'g'), ('SUGAR_G', t.s, 'g'), ('SODIUM_MG', t.na, 'mg')
) AS x(code, total, unit)
WHERE x.total IS NOT NULL;

-- ------------------------------------------------------------------ 7. goals
-- Children: ACTIVITY / SLEEP / HYDRATION only, and only AT_LEAST or MAINTAIN (D-047).
-- No calorie ceiling, no weight goal, no "at most" anything for a child — not because the
-- database would stop it, but because the product refuses to express it.
INSERT INTO wellness_goal (household_member_id, metric_code, direction, target_value, period,
                           started_on, is_active, created_by_user_id)
SELECT pg_temp.id(g.mk), g.metric, g.dir, g.target, g.period,
       pg_temp.ist_today() - 21, true, pg_temp.id(r.ak)
FROM (VALUES
  ('mem_raghav',  'STEPS',          'AT_LEAST', 8000, 'DAILY'),
  ('mem_raghav',  'WATER_ML',       'AT_LEAST', 2500, 'DAILY'),
  ('mem_meera',   'SLEEP_MINUTES',  'AT_LEAST',  420, 'DAILY'),
  ('mem_meera',   'FIBRE_G',        'AT_LEAST',   25, 'DAILY'),
  ('mem_krishnan','WATER_ML',       'AT_LEAST', 1800, 'DAILY'),
  ('mem_krishnan','ACTIVE_MINUTES', 'AT_LEAST',   20, 'DAILY'),
  ('mem_lakshmi', 'STEPS',          'AT_LEAST', 4000, 'DAILY'),
  ('mem_arjun',   'WORKOUT_MINUTES','AT_LEAST',   45, 'DAILY'),
  ('mem_arjun',   'PROTEIN_G',      'AT_LEAST',   90, 'DAILY'),
  ('mem_vikram',  'STEPS',          'AT_LEAST', 7000, 'DAILY'),
  ('mem_vikram',  'SODIUM_MG',      'AT_MOST',  2000, 'DAILY'),
  ('mem_divya',   'STEPS',          'AT_LEAST', 9000, 'DAILY'),
  ('mem_kavya',   'WATER_ML',       'AT_LEAST', 2200, 'DAILY'),
  ('mem_aarav',   'WATER_ML',       'AT_LEAST', 1200, 'DAILY'),
  ('mem_aarav',   'SLEEP_MINUTES',  'AT_LEAST',  540, 'DAILY'),
  ('mem_ananya',  'STEPS',          'AT_LEAST', 8000, 'DAILY'),
  ('mem_ananya',  'SLEEP_MINUTES',  'AT_LEAST',  510, 'DAILY')
) AS g(mk, metric, dir, target, period)
JOIN recorder r ON r.mk = g.mk;

-- ------------------------------------------------------------------ 8. habits
-- Created three weeks back, like the goals: a habit only counts from the day it was created
-- (HabitService.consistency / daySummaries), so habits "born" at seed time would disown the
-- week of completions below and read as "done on 1 of 1 days".
INSERT INTO habit (id, household_member_id, name, cadence, is_active, created_by_user_id, created_at)
SELECT ('f0de0000-0000-4000-8000-0000000000' || lpad(h.rn::text, 2, '0'))::uuid,
       pg_temp.id(h.mk), h.nm, h.cadence, true, pg_temp.id(r.ak), now() - interval '21 days'
FROM (VALUES
  (1, 'mem_raghav',  'Walk after dinner',      'DAILY'),
  (2, 'mem_meera',   'Fruit at breakfast',     'DAILY'),
  (3, 'mem_krishnan','Morning walk',           'DAILY'),
  (4, 'mem_krishnan','Blood pressure reading', 'WEEKDAYS'),
  (5, 'mem_lakshmi', 'Evening stretches',      'DAILY'),
  (6, 'mem_arjun',   'Gym session',            'WEEKDAYS'),
  (7, 'mem_vikram',  'Stand up every hour',    'WEEKDAYS'),
  (8, 'mem_divya',   'Yoga',                   'DAILY'),
  (9, 'mem_kavya',   'Evening walk',           'DAILY'),
  (10,'mem_aarav',   'Drink water at school',  'DAILY'),
  (11,'mem_ananya',  'Read before bed',        'DAILY')
) AS h(rn, mk, nm, cadence)
JOIN recorder r ON r.mk = h.mk;

-- Completions with real gaps: the product says "done on 4 of 7 days", so the data must
-- actually have missing days rather than a perfect week nobody would believe.
INSERT INTO habit_completion (habit_id, completed_on, recorded_by_user_id, recorded_at)
SELECT h.id, pg_temp.ist_today() - d, h.created_by_user_id, now()
FROM habit h
JOIN household_member hm ON hm.id = h.household_member_id AND hm.household_id = pg_temp.id('household')
CROSS JOIN generate_series(0, 6) AS d
WHERE (h.name = 'Walk after dinner'      AND d <> 3)
   OR (h.name = 'Fruit at breakfast'     AND d NOT IN (1, 4))
   OR (h.name = 'Morning walk'           AND d <> 2)
   OR (h.name = 'Blood pressure reading' AND d IN (0, 2, 4, 6))
   OR (h.name = 'Evening stretches'      AND d IN (1, 3, 4, 5))
   OR (h.name = 'Gym session'            AND d IN (0, 1, 3, 5, 6))
   OR (h.name = 'Stand up every hour'    AND d IN (0, 2, 4))
   OR (h.name = 'Yoga'                   AND d NOT IN (1, 5))
   OR (h.name = 'Evening walk'           AND d IN (0, 1, 2, 4, 6))
   OR (h.name = 'Drink water at school'  AND d <> 6)
   OR (h.name = 'Read before bed'        AND d IN (0, 1, 2, 4, 5));

-- ------------------------------------------------------------------ 9. sharing
-- Only the subject grants, and never about a child. This matrix produces every relationship
-- the UI has a state for: SELF, GUARDIAN, CAREGIVER (a MANAGE grant), SHARED (VIEW only)
-- and NONE — Arjun and Kavya deliberately share nothing, because "hasn't shared anything
-- with you" is a designed screen that needs a real person behind it.
INSERT INTO health_access_grant (subject_member_id, audience_member_id, scope, access_level,
                                 granted_at, granted_by_user_id)
SELECT pg_temp.id(g.subject), pg_temp.id(g.audience), g.scope, g.lvl,
       now() - interval '14 days', pg_temp.id(r.ak)
FROM (VALUES
  -- The grandfather asks his son for help: MANAGE is what makes Raghav a caregiver.
  ('mem_krishnan','mem_raghav', 'HYDRATION','MANAGE'),
  ('mem_krishnan','mem_raghav', 'ACTIVITY', 'MANAGE'),
  ('mem_krishnan','mem_raghav', 'CLINICAL', 'VIEW'),
  ('mem_krishnan','mem_lakshmi','CLINICAL', 'VIEW'),
  -- The grandmother shares read-only with her daughter-in-law.
  ('mem_lakshmi', 'mem_meera',  'ACTIVITY', 'VIEW'),
  ('mem_lakshmi', 'mem_meera',  'SLEEP',    'VIEW'),
  -- The parents share with each other.
  ('mem_meera',   'mem_raghav', 'NUTRITION','VIEW'),
  ('mem_meera',   'mem_raghav', 'ACTIVITY', 'VIEW'),
  ('mem_raghav',  'mem_meera',  'NUTRITION','VIEW'),
  ('mem_raghav',  'mem_meera',  'ACTIVITY', 'VIEW'),
  ('mem_raghav',  'mem_meera',  'SLEEP',    'VIEW'),
  -- A sister shares activity only; a brother shares habits with his brother.
  ('mem_divya',   'mem_meera',  'ACTIVITY', 'VIEW'),
  ('mem_vikram',  'mem_raghav', 'ACTIVITY', 'VIEW'),
  ('mem_vikram',  'mem_raghav', 'HABITS',   'VIEW')
) AS g(subject, audience, scope, lvl)
JOIN recorder r ON r.mk = g.subject;

COMMIT;

-- ------------------------------------------------------------------ verification
\echo ''
\echo '=== Demo family seeded ==='
SELECT 'members'                     AS item, count(*)::text AS value FROM household_member WHERE household_id = 'd0de0000-0000-4000-8000-000000000001'
UNION ALL SELECT 'adults with logins', count(*)::text FROM household_member WHERE household_id = 'd0de0000-0000-4000-8000-000000000001' AND user_account_id IS NOT NULL
UNION ALL SELECT 'observations',       count(*)::text FROM health_observation o JOIN household_member m ON m.id = o.household_member_id WHERE m.household_id = 'd0de0000-0000-4000-8000-000000000001'
UNION ALL SELECT 'meals',              count(*)::text FROM meal WHERE household_id = 'd0de0000-0000-4000-8000-000000000001'
UNION ALL SELECT 'portions awaiting',  count(*)::text FROM meal_portion p JOIN meal m ON m.id = p.meal_id WHERE m.household_id = 'd0de0000-0000-4000-8000-000000000001' AND p.derived_at IS NULL
UNION ALL SELECT 'goals',              count(*)::text FROM wellness_goal g JOIN household_member m ON m.id = g.household_member_id WHERE m.household_id = 'd0de0000-0000-4000-8000-000000000001'
UNION ALL SELECT 'habit completions',  count(*)::text FROM habit_completion hc JOIN habit h ON h.id = hc.habit_id JOIN household_member m ON m.id = h.household_member_id WHERE m.household_id = 'd0de0000-0000-4000-8000-000000000001'
UNION ALL SELECT 'sharing grants',     count(*)::text FROM health_access_grant g JOIN household_member m ON m.id = g.subject_member_id WHERE m.household_id = 'd0de0000-0000-4000-8000-000000000001'
UNION ALL SELECT 'child BODY/CLINICAL (must be 0)', count(*)::text FROM health_observation o JOIN household_member m ON m.id = o.household_member_id WHERE m.member_type = 'CHILD' AND o.metric_category IN ('BODY','CLINICAL');
\echo ''
\echo 'Sign in with any of these (password for all: DemoFamily2026!seed):'
\echo '  krishnan.iyer.demo@example.com   grandfather (older-adult tier, has a caregiver)'
\echo '  lakshmi.iyer.demo@example.com    grandmother'
\echo '  raghav.iyer.demo@example.com     father  (owner; guards Aarav; caregiver for Krishnan)'
\echo '  meera.iyer.demo@example.com      mother  (guards Ananya)'
\echo '  arjun.iyer.demo@example.com      brother (shares nothing - the NONE state)'
\echo '  vikram.iyer.demo@example.com     brother'
\echo '  divya.iyer.demo@example.com      sister'
\echo '  kavya.iyer.demo@example.com      sister  (shares nothing)'
\echo '  Children Aarav (son) and Ananya (daughter) have no logins, by design.'
