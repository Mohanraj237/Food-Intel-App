#!/usr/bin/env node
/**
 * Demo seed — one week in the life of a multi-generational household.
 *
 * WHY THIS DRIVES THE REST API RATHER THAN WRITING SQL
 * ----------------------------------------------------
 * Every health write in this product passes a gate that lives in the application layer,
 * not in the database: the consent gate (D-048), the metric age gate (D-036), the child
 * goal rule (D-047), the access policy (D-044) and the D-041 meal-portion derivation
 * (another adult's share is NOT written into their log until they confirm it themselves).
 * Seeding through SQL would happily produce a database the application itself would have
 * refused to create — a demo that lies. Going through the API means the demo data is, by
 * construction, data the product agrees is legal, and the seeder doubles as a contract test.
 *
 * LOCAL DEVELOPMENT ONLY
 * ----------------------
 * The credentials below are deliberately weak, obviously fake and reserved-domain
 * (example.com, RFC 2606). They exist so a developer can sign in and look at a populated
 * app. Never reuse them anywhere that is deployed.
 *
 * USAGE
 *   node tools/demo-seed/seed-demo-family.mjs
 *   node tools/demo-seed/seed-demo-family.mjs --api http://localhost:8090/api/v1
 *
 * Re-running is safe: if the owner account already exists the script stops rather than
 * creating a second household.
 */

const API = argOf('--api') ?? 'http://localhost:8090/api/v1';
const PASSWORD = 'DemoFamily2026!seed';
const HOUSEHOLD_NAME = 'Iyer family';

function argOf(flag) {
  const i = process.argv.indexOf(flag);
  return i >= 0 ? process.argv[i + 1] : undefined;
}

// ---------------------------------------------------------------- http helper

async function api(method, path, { token, body } = {}) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const parsed = text ? safeJson(text) : null;
  if (!res.ok) {
    const err = new Error(`${method} ${path} -> ${res.status} ${text.slice(0, 400)}`);
    err.status = res.status;
    err.body = parsed;
    throw err;
  }
  return parsed;
}

function safeJson(t) {
  try {
    return JSON.parse(t);
  } catch {
    return t;
  }
}

// ---------------------------------------------------------------- time helpers
// The household lives in Asia/Kolkata (D-053: "today" is the household's day, not UTC's).
// Timestamps are built as IST wall-clock and converted to an instant by subtracting +05:30.

const IST_OFFSET_MIN = 5 * 60 + 30;

/** `daysAgo` 0 = today. Returns an ISO instant for that IST wall-clock time. */
function istAt(daysAgo, hour, minute = 0) {
  const nowUtcMs = Date.now();
  const istNow = new Date(nowUtcMs + IST_OFFSET_MIN * 60_000);
  const y = istNow.getUTCFullYear();
  const m = istNow.getUTCMonth();
  const d = istNow.getUTCDate() - daysAgo;
  const asUtcWallClock = Date.UTC(y, m, d, hour, minute, 0);
  return new Date(asUtcWallClock - IST_OFFSET_MIN * 60_000).toISOString();
}

/** ISO date (yyyy-mm-dd) of the IST day `daysAgo` days back — for habit completions. */
function istDate(daysAgo) {
  const istNow = new Date(Date.now() + IST_OFFSET_MIN * 60_000);
  const d = new Date(Date.UTC(istNow.getUTCFullYear(), istNow.getUTCMonth(), istNow.getUTCDate() - daysAgo));
  return d.toISOString().slice(0, 10);
}

const DAYS = [6, 5, 4, 3, 2, 1, 0]; // a full week, oldest first

// ---------------------------------------------------------------- the family
//
// Eight people across three generations. Ages are expressed only as age bands —
// the product deliberately never stores a date of birth (CH-006).

const PEOPLE = {
  raghav:   { label: 'Raghav',  email: 'raghav.iyer.demo@example.com',  role: 'father',      ageBand: 'A31_45',  sex: 'MALE',   diet: 'VEG',         activity: 'MODERATELY_ACTIVE' },
  meera:    { label: 'Meera',   email: 'meera.iyer.demo@example.com',   role: 'mother',      ageBand: 'A31_45',  sex: 'FEMALE', diet: 'VEG',         activity: 'LIGHTLY_ACTIVE' },
  krishnan: { label: 'Krishnan',email: 'krishnan.iyer.demo@example.com',role: 'grandfather', ageBand: 'A60_PLUS',sex: 'MALE',   diet: 'VEG',         activity: 'SEDENTARY' },
  lakshmi:  { label: 'Lakshmi', email: 'lakshmi.iyer.demo@example.com', role: 'grandmother', ageBand: 'A60_PLUS',sex: 'FEMALE', diet: 'VEG',         activity: 'LIGHTLY_ACTIVE' },
  arjun:    { label: 'Arjun',   email: 'arjun.iyer.demo@example.com',   role: 'brother',     ageBand: 'A18_30',  sex: 'MALE',   diet: 'EGGETARIAN',  activity: 'VERY_ACTIVE' },
  divya:    { label: 'Divya',   email: 'divya.iyer.demo@example.com',   role: 'sister',      ageBand: 'A18_30',  sex: 'FEMALE', diet: 'VEG',         activity: 'MODERATELY_ACTIVE' },
};

const CHILDREN = {
  aarav:  { label: 'Aarav',  role: 'son',      ageBand: 'C7_9',   sex: 'MALE',   guardian: 'raghav' },
  ananya: { label: 'Ananya', role: 'daughter', ageBand: 'C10_12', sex: 'FEMALE', guardian: 'meera' },
};

const log = (...a) => console.log(...a);

// ---------------------------------------------------------------- main

async function main() {
  log(`Seeding demo family against ${API}\n`);

  // 1. Accounts -------------------------------------------------------------
  const accounts = {};
  for (const [key, p] of Object.entries(PEOPLE)) {
    accounts[key] = await register(p.email);
    log(`  account  ${p.label.padEnd(9)} ${p.email}`);
  }

  // 2. Household, owned by the father ---------------------------------------
  const household = await api('POST', '/households', {
    token: accounts.raghav.accessToken,
    body: { name: HOUSEHOLD_NAME, ownerDisplayLabel: PEOPLE.raghav.label },
  });
  const householdId = household.id ?? household.householdId;
  log(`\n  household "${HOUSEHOLD_NAME}" ${householdId}`);

  // 3. The other adults join by invitation (an adult is never added by proxy — D-002)
  for (const key of ['meera', 'krishnan', 'lakshmi', 'arjun', 'divya']) {
    const invite = await api('POST', `/households/${householdId}/members`, {
      token: accounts.raghav.accessToken,
      body: { memberType: 'ADULT', email: PEOPLE[key].email },
    });
    await api('POST', '/households/invitations/accept', {
      token: accounts[key].accessToken,
      body: { token: invite.token, displayLabel: PEOPLE[key].label },
    });
    log(`  joined   ${PEOPLE[key].label} (${PEOPLE[key].role})`);
  }

  // 4. Children are added by the adult who will guard them -------------------
  for (const [key, c] of Object.entries(CHILDREN)) {
    await api('POST', `/households/${householdId}/members`, {
      token: accounts[c.guardian].accessToken,
      body: {
        memberType: 'CHILD',
        displayLabel: c.label,
        relationship: 'PARENT',
        authorityBasis: 'Parent, demo data — guardian attestation recorded at seed time',
      },
    });
    log(`  added    ${c.label} (${c.role}), guarded by ${PEOPLE[c.guardian].label}`);
  }

  // 5. Resolve every member id ---------------------------------------------
  const roster = await api('GET', `/households/${householdId}/members`, { token: accounts.raghav.accessToken });
  const memberId = {};
  for (const m of roster) {
    const entry =
      Object.entries(PEOPLE).find(([, p]) => p.label === m.displayLabel) ??
      Object.entries(CHILDREN).find(([, c]) => c.label === m.displayLabel);
    if (entry) memberId[entry[0]] = m.id ?? m.memberId;
  }
  log(`\n  roster resolved: ${Object.keys(memberId).length} members`);

  // 6. Consent — adults for themselves, guardians for their child ------------
  for (const [key, p] of Object.entries(PEOPLE)) {
    for (const purpose of ['ACCOUNT', 'PERSONAL_FIT', 'HEALTH_TRACKING']) {
      await grantConsent(accounts[key].accessToken, memberId[key], purpose);
    }
  }
  for (const [key, c] of Object.entries(CHILDREN)) {
    for (const purpose of ['CHILD_FOOD_SUITABILITY', 'CHILD_WELLNESS_TRACKING']) {
      await grantConsent(accounts[c.guardian].accessToken, memberId[key], purpose, {
        guardianAttestation: true,
        note: 'Demo seed — guardian attested (D-001 provisional mechanism)',
      });
    }
  }
  log('  consent granted (adults self, children guardian-attested)');

  // 7. Profiles -------------------------------------------------------------
  const profileExtras = {
    raghav: { goals: ['REDUCE_SODIUM', 'INCREASE_FIBRE'], allergens: [], conditions: [] },
    meera: { goals: ['INCREASE_PROTEIN'], allergens: ['PEANUT'], conditions: [] },
    krishnan: { goals: ['REDUCE_ADDED_SUGAR', 'REDUCE_SODIUM'], allergens: [], conditions: ['Type 2 diabetes'] },
    lakshmi: { goals: ['GENERAL_BALANCED_EATING'], allergens: ['MILK'], conditions: ['High blood pressure'] },
    arjun: { goals: ['INCREASE_PROTEIN'], allergens: [], conditions: [] },
    divya: { goals: ['GENERAL_BALANCED_EATING'], allergens: ['TREE_NUT'], conditions: [] },
  };
  for (const [key, p] of Object.entries(PEOPLE)) {
    await putProfile(accounts[key].accessToken, householdId, memberId[key], {
      ageBand: p.ageBand,
      sex: p.sex,
      dietaryPreference: p.diet,
      activityLevel: p.activity,
      ...profileExtras[key],
    });
  }
  for (const [key, c] of Object.entries(CHILDREN)) {
    await putProfile(accounts[c.guardian].accessToken, householdId, memberId[key], {
      ageBand: c.ageBand,
      sex: c.sex,
      dietaryPreference: 'VEG',
      goals: [],
      allergens: key === 'aarav' ? ['MILK'] : [],
      conditions: [],
      // activityLevel is deliberately not set for a child: it has no child use that
      // does not drift towards an energy target (D-047).
    });
  }
  log('  profiles written (age bands, diet, allergens, conditions)');

  // 8. A week of observations ----------------------------------------------
  let observationCount = 0;
  const rec = async (ownerKey, subjectKey, metricCode, value, effectiveAt) => {
    await api('POST', `/wellness/members/${memberId[subjectKey]}/observations`, {
      token: accounts[ownerKey].accessToken,
      // Rounded because the column is numeric(12,3): plain JS arithmetic such as
      // 78.4 - 0.6 yields 77.80000000000001, which the API rejects as too precise.
      body: { metricCode, value: Math.round(value * 1000) / 1000, effectiveAt },
    });
    observationCount++;
  };

  for (const d of DAYS) {
    // --- Father: consistent, one missed water day so "not logged" is visible
    await rec('raghav', 'raghav', 'STEPS', 7200 + d * 340, istAt(d, 21, 10));
    await rec('raghav', 'raghav', 'SLEEP_MINUTES', 400 + (d % 3) * 25, istAt(d, 7, 0));
    if (d !== 4) await rec('raghav', 'raghav', 'WATER_ML', 2200 + (d % 2) * 300, istAt(d, 20, 0));
    if (d % 3 === 0) await rec('raghav', 'raghav', 'WEIGHT_KG', 78.4 - d * 0.1, istAt(d, 7, 15));
    if (d % 2 === 0) await rec('raghav', 'raghav', 'MOOD_SCORE', 3 + (d % 3 === 0 ? 1 : 0), istAt(d, 22, 0));

    // --- Mother
    await rec('meera', 'meera', 'STEPS', 6100 + d * 210, istAt(d, 21, 0));
    await rec('meera', 'meera', 'SLEEP_MINUTES', 380 + (d % 4) * 20, istAt(d, 6, 40));
    await rec('meera', 'meera', 'WATER_ML', 1800 + (d % 3) * 250, istAt(d, 20, 30));
    if (d % 3 === 1) await rec('meera', 'meera', 'WEIGHT_KG', 61.2 + d * 0.05, istAt(d, 7, 5));
    if (d % 2 === 1) await rec('meera', 'meera', 'MOOD_SCORE', 4, istAt(d, 22, 10));

    // --- Grandfather: lower activity, clinical readings (adult-only metrics)
    await rec('krishnan', 'krishnan', 'STEPS', 2400 + d * 120, istAt(d, 19, 0));
    await rec('krishnan', 'krishnan', 'SLEEP_MINUTES', 330 + (d % 3) * 30, istAt(d, 6, 0));
    await rec('krishnan', 'krishnan', 'WATER_ML', 1400 + (d % 2) * 200, istAt(d, 19, 30));
    await rec('krishnan', 'krishnan', 'ACTIVE_MINUTES', 15 + (d % 3) * 5, istAt(d, 8, 0));
    if (d % 2 === 0) {
      await rec('krishnan', 'krishnan', 'SYSTOLIC_MMHG', 132 + (d % 3), istAt(d, 8, 5));
      await rec('krishnan', 'krishnan', 'DIASTOLIC_MMHG', 84 + (d % 2), istAt(d, 8, 5));
      await rec('krishnan', 'krishnan', 'RESTING_HEART_RATE_BPM', 72 + (d % 4), istAt(d, 8, 6));
    }
    if (d % 3 === 0) await rec('krishnan', 'krishnan', 'BLOOD_GLUCOSE_MGDL', 126 + d, istAt(d, 7, 30));

    // --- Grandmother
    await rec('lakshmi', 'lakshmi', 'STEPS', 3600 + d * 150, istAt(d, 19, 15));
    await rec('lakshmi', 'lakshmi', 'SLEEP_MINUTES', 360 + (d % 3) * 20, istAt(d, 6, 10));
    await rec('lakshmi', 'lakshmi', 'WATER_ML', 1500 + (d % 3) * 150, istAt(d, 19, 45));
    if (d % 2 === 1) {
      await rec('lakshmi', 'lakshmi', 'SYSTOLIC_MMHG', 128 + (d % 4), istAt(d, 8, 20));
      await rec('lakshmi', 'lakshmi', 'DIASTOLIC_MMHG', 80 + (d % 3), istAt(d, 8, 20));
    }

    // --- Brother: the athlete of the household
    await rec('arjun', 'arjun', 'STEPS', 11000 + d * 420, istAt(d, 22, 0));
    await rec('arjun', 'arjun', 'SLEEP_MINUTES', 420 + (d % 2) * 30, istAt(d, 7, 30));
    await rec('arjun', 'arjun', 'WATER_ML', 3100 + (d % 2) * 400, istAt(d, 21, 0));
    if (d !== 2 && d !== 5) {
      await rec('arjun', 'arjun', 'WORKOUT_MINUTES', 45 + (d % 3) * 15, istAt(d, 18, 30));
      await rec('arjun', 'arjun', 'DISTANCE_KM', 6.2 + (d % 3) * 1.4, istAt(d, 18, 30));
    }
    await rec('arjun', 'arjun', 'ACTIVE_MINUTES', 55 + (d % 4) * 10, istAt(d, 20, 0));

    // --- Sister
    await rec('divya', 'divya', 'STEPS', 8300 + d * 260, istAt(d, 21, 30));
    await rec('divya', 'divya', 'SLEEP_MINUTES', 405 + (d % 3) * 25, istAt(d, 7, 10));
    await rec('divya', 'divya', 'WATER_ML', 2400 + (d % 2) * 200, istAt(d, 20, 15));
    if (d % 2 === 0) await rec('divya', 'divya', 'MOOD_SCORE', 4, istAt(d, 22, 30));

    // --- Children: guardian-entered, and never a body or clinical metric (D-036)
    await rec('raghav', 'aarav', 'STEPS', 7400 + d * 200, istAt(d, 19, 0));
    await rec('raghav', 'aarav', 'SLEEP_MINUTES', 560 + (d % 3) * 15, istAt(d, 7, 0));
    await rec('raghav', 'aarav', 'WATER_ML', 1100 + (d % 3) * 150, istAt(d, 18, 0));
    if (d % 3 === 0) await rec('raghav', 'aarav', 'MOOD_SCORE', 4, istAt(d, 20, 0));

    await rec('meera', 'ananya', 'STEPS', 8600 + d * 180, istAt(d, 19, 30));
    await rec('meera', 'ananya', 'SLEEP_MINUTES', 530 + (d % 4) * 20, istAt(d, 6, 50));
    if (d !== 3) await rec('meera', 'ananya', 'WATER_ML', 1250 + (d % 2) * 200, istAt(d, 18, 30));
    if (d % 2 === 1) await rec('meera', 'ananya', 'MOOD_SCORE', 5, istAt(d, 20, 30));
  }
  log(`  ${observationCount} observations across 7 days`);

  // 9. Meals, including the D-041 family-apportioning story ------------------
  const products = await pickProducts(accounts.raghav.accessToken);
  let mealCount = 0;
  let awaitingCount = 0;

  const homeCooked = (customName, quantityG, per100) => ({ customName, quantityG, nutritionPer100: per100 });

  for (const d of DAYS) {
    // Breakfast, logged by the mother for herself and the children she may log for.
    await api('POST', '/nutrition/meals', {
      token: accounts.meera.accessToken,
      body: {
        householdId,
        mealType: 'BREAKFAST',
        name: 'Idli and sambar',
        consumedAt: istAt(d, 8, 15),
        items: [
          homeCooked('Idli', 180, { energyKcal: 148, proteinG: 4.4, carbohydrateG: 30, fatG: 0.6, fibreG: 1.4, sugarG: 0.5, sodiumMg: 210 }),
          homeCooked('Sambar', 200, { energyKcal: 85, proteinG: 3.8, carbohydrateG: 12, fatG: 2.4, fibreG: 3.1, sugarG: 2.2, sodiumMg: 420 }),
        ],
        portions: [
          { householdMemberId: memberId.meera, portionFraction: 0.5 },
          { householdMemberId: memberId.ananya, portionFraction: 0.5 },
        ],
      },
    });
    mealCount++;

    // Dinner, logged by the father: himself, the child he guards, and — the interesting
    // case — his wife, whose share stays out of her log until she confirms it (D-041).
    const dinnerItems = [
      homeCooked('Dal tadka', 220, { energyKcal: 118, proteinG: 6.2, carbohydrateG: 14, fatG: 4.1, fibreG: 4.0, sugarG: 1.6, sodiumMg: 380 }),
      homeCooked('Jeera rice', 260, { energyKcal: 165, proteinG: 3.4, carbohydrateG: 33, fatG: 2.3, fibreG: 1.1, sugarG: 0.3, sodiumMg: 190 }),
    ];
    if (products.length && d % 2 === 0) {
      dinnerItems.push({ productId: products[d % products.length], quantityG: 40 });
    }

    const dinner = await api('POST', '/nutrition/meals', {
      token: accounts.raghav.accessToken,
      body: {
        householdId,
        mealType: 'DINNER',
        name: 'Family dinner',
        consumedAt: istAt(d, 20, 30),
        items: dinnerItems,
        portions: [
          { householdMemberId: memberId.raghav, portionFraction: 0.5 },
          { householdMemberId: memberId.aarav, portionFraction: 0.25 },
          { householdMemberId: memberId.meera, portionFraction: 0.5 },
          { householdMemberId: memberId.krishnan, portionFraction: 0.5 },
        ],
      },
    });
    mealCount++;

    // The mother confirms her share on most days, leaving the two most recent days
    // awaiting so the "Waiting for you" state is visible in the running app.
    if (d > 1) {
      await api('POST', `/nutrition/meals/${dinner.id}/confirm`, { token: accounts.meera.accessToken });
    } else {
      awaitingCount++;
    }
    // The grandfather never confirms: his share stays pending, which is exactly what a
    // caregiver-supported elder's log looks like before someone helps him.
    awaitingCount++;
  }
  log(`  ${mealCount} meals (${awaitingCount} portions awaiting confirmation — D-041)`);

  // 10. Goals ---------------------------------------------------------------
  const goals = [
    ['raghav', 'raghav', 'STEPS', 'AT_LEAST', 8000, 'DAILY'],
    ['raghav', 'raghav', 'WATER_ML', 'AT_LEAST', 2500, 'DAILY'],
    ['meera', 'meera', 'SLEEP_MINUTES', 'AT_LEAST', 420, 'DAILY'],
    ['meera', 'meera', 'FIBRE_G', 'AT_LEAST', 25, 'DAILY'],
    ['krishnan', 'krishnan', 'WATER_ML', 'AT_LEAST', 1800, 'DAILY'],
    ['krishnan', 'krishnan', 'ACTIVE_MINUTES', 'AT_LEAST', 20, 'DAILY'],
    ['lakshmi', 'lakshmi', 'STEPS', 'AT_LEAST', 4000, 'DAILY'],
    ['arjun', 'arjun', 'WORKOUT_MINUTES', 'AT_LEAST', 45, 'DAILY'],
    ['arjun', 'arjun', 'PROTEIN_G', 'AT_LEAST', 90, 'DAILY'],
    ['divya', 'divya', 'STEPS', 'AT_LEAST', 9000, 'DAILY'],
    // Children: only ACTIVITY / SLEEP / HYDRATION, only AT_LEAST or MAINTAIN (D-047).
    ['raghav', 'aarav', 'WATER_ML', 'AT_LEAST', 1200, 'DAILY'],
    ['raghav', 'aarav', 'SLEEP_MINUTES', 'AT_LEAST', 540, 'DAILY'],
    ['meera', 'ananya', 'STEPS', 'AT_LEAST', 8000, 'DAILY'],
    ['meera', 'ananya', 'SLEEP_MINUTES', 'AT_LEAST', 510, 'DAILY'],
  ];
  for (const [owner, subject, metricCode, direction, targetValue, period] of goals) {
    await api('POST', '/goals', {
      token: accounts[owner].accessToken,
      body: { memberId: memberId[subject], metricCode, direction, targetValue, period },
    });
  }
  log(`  ${goals.length} goals`);

  // 11. Habits, with a week of completions -----------------------------------
  const habits = [
    ['raghav', 'raghav', 'Walk after dinner', 'DAILY', [6, 5, 4, 2, 1, 0]],
    ['meera', 'meera', 'Fruit at breakfast', 'DAILY', [6, 5, 3, 2, 0]],
    ['krishnan', 'krishnan', 'Morning walk', 'DAILY', [6, 5, 4, 3, 1, 0]],
    ['krishnan', 'krishnan', 'Blood pressure reading', 'WEEKDAYS', [6, 4, 2, 0]],
    ['lakshmi', 'lakshmi', 'Evening stretches', 'DAILY', [5, 4, 3, 1]],
    ['arjun', 'arjun', 'Gym session', 'WEEKDAYS', [6, 5, 3, 1, 0]],
    ['divya', 'divya', 'Yoga', 'DAILY', [6, 4, 3, 2, 0]],
    ['raghav', 'aarav', 'Drink water at school', 'DAILY', [6, 5, 4, 3, 2, 1, 0]],
    ['meera', 'ananya', 'Read before bed', 'DAILY', [6, 5, 4, 2, 1]],
  ];
  let completionCount = 0;
  for (const [owner, subject, name, cadence, days] of habits) {
    const habit = await api('POST', '/habits', {
      token: accounts[owner].accessToken,
      body: { memberId: memberId[subject], name, cadence },
    });
    for (const d of days) {
      await api('PUT', `/habits/${habit.id}/completions/${istDate(d)}`, { token: accounts[owner].accessToken });
      completionCount++;
    }
  }
  log(`  ${habits.length} habits, ${completionCount} completions`);

  // 12. Sharing — only the subject may grant (D-044) --------------------------
  //
  // This matrix is the point of the demo: it produces every relationship the UI has a
  // state for — SELF, GUARDIAN, CAREGIVER (a MANAGE grant), SHARED (VIEW only) and NONE.
  const grants = [
    // The grandfather asks his son to help: MANAGE turns Raghav into a caregiver.
    ['krishnan', 'raghav', 'HYDRATION', 'MANAGE'],
    ['krishnan', 'raghav', 'ACTIVITY', 'MANAGE'],
    ['krishnan', 'raghav', 'CLINICAL', 'VIEW'],
    ['krishnan', 'lakshmi', 'CLINICAL', 'VIEW'],
    // The grandmother shares read-only with her daughter-in-law.
    ['lakshmi', 'meera', 'ACTIVITY', 'VIEW'],
    ['lakshmi', 'meera', 'SLEEP', 'VIEW'],
    // The parents share with each other.
    ['meera', 'raghav', 'NUTRITION', 'VIEW'],
    ['meera', 'raghav', 'ACTIVITY', 'VIEW'],
    ['raghav', 'meera', 'NUTRITION', 'VIEW'],
    ['raghav', 'meera', 'ACTIVITY', 'VIEW'],
    ['raghav', 'meera', 'SLEEP', 'VIEW'],
    // The sister shares her activity only.
    ['divya', 'meera', 'ACTIVITY', 'VIEW'],
    // Arjun deliberately shares nothing: the "hasn't shared anything with you" state
    // is a designed screen and needs a real person behind it.
  ];
  for (const [subject, audience, scope, level] of grants) {
    await api('POST', `/members/${memberId[subject]}/access-grants`, {
      token: accounts[subject].accessToken,
      body: { audienceMemberId: memberId[audience], scope, level },
    });
  }
  log(`  ${grants.length} sharing grants (Arjun shares nothing, by design)`);

  // ---------------------------------------------------------------- summary
  log('\n' + '='.repeat(66));
  log('Demo family seeded. Local sign-in credentials (development only):\n');
  for (const [key, p] of Object.entries(PEOPLE)) {
    log(`  ${p.role.padEnd(12)} ${p.email.padEnd(34)} ${PASSWORD}`);
  }
  log(`\n  Children (no accounts, by design): ${Object.values(CHILDREN).map((c) => `${c.label} (${c.role})`).join(', ')}`);
  log(`  Household id: ${householdId}`);
  log('\n  Try: sign in as the father to see a full household; as Arjun to see');
  log('  what someone who shares nothing looks like; as Krishnan for the');
  log('  older-adult experience with a caregiver.');
  log('='.repeat(66));
}

// ---------------------------------------------------------------- steps

async function register(email) {
  try {
    return await api('POST', '/auth/register', { body: { email, password: PASSWORD } });
  } catch (e) {
    if (e.status === 409 || /exists|conflict|duplicate/i.test(String(e.message))) {
      throw new Error(
        `${email} already exists — the demo family appears to be seeded already.\n` +
          `Reset the local database (docker compose down -v && up) before re-seeding.`,
      );
    }
    throw e;
  }
}

async function grantConsent(token, subjectMemberId, purposeCode, evidence = {}) {
  try {
    await api('POST', `/consent/members/${subjectMemberId}/grants`, { token, body: { purposeCode, evidence } });
  } catch (e) {
    if (e.status === 409) return; // already active
    throw e;
  }
}

async function putProfile(token, householdId, memberIdValue, profile) {
  await api('PUT', `/households/${householdId}/members/${memberIdValue}/profile`, { token, body: profile });
}

/** A few real catalogue products, so at least some meal items are genuine packaged food. */
async function pickProducts(token) {
  try {
    const res = await api('GET', '/search?q=milk&size=5', { token });
    const items = res?.items ?? res?.results ?? res?.content ?? [];
    return items.map((p) => p.productId ?? p.id).filter(Boolean).slice(0, 4);
  } catch {
    return [];
  }
}

main().catch((e) => {
  console.error('\nSEED FAILED:', e.message);
  process.exit(1);
});
