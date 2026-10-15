#!/usr/bin/env node
/**
 * Re-fetches every product already in the snapshot, one GTIN at a time, from the Open Food
 * Facts product API — for fields the original search-API fetch did not request
 * (nova_group, additives_tags, ingredients_analysis_tags, serving_size, serving_quantity,
 * labels_tags, nutriscore_grade; see fetch-openfoodfacts.mjs FIELDS). Consumed by
 * backend/.../catalog/seed/OpenFoodFactsEnrichmentRunner.java through the merged snapshot.
 *
 * Open Food Facts data is licensed under the Open Database License (ODbL) 1.0 —
 * https://opendatacommons.org/licenses/odbl/1.0/ — attribution is recorded per row on
 * product_source.license_note (D-031).
 *
 * OFFLINE tooling, never a runtime dependency. Nothing here talks to Postgres.
 *
 * Usage:
 *   node refetch-openfoodfacts.mjs [--in=data/openfoodfacts-india.jsonl] [--out=data/refetch.jsonl]
 *                                  [--delay=1500] [--retries=6] [--resume=true] [--merge]
 *
 *   --resume   continue from data/refetch.progress.json (the default when the file exists)
 *   --merge    after fetching, fold the re-fetched rows into --in by product code
 *              (fields present in the re-fetched row replace the old ones; rows OFF no
 *              longer returns are kept unchanged) — then copy the result to
 *              backend/src/main/resources/db/seed/ as the README describes.
 *
 * Respectful-use notes (a shared community API, not a CDN): one request at a time, a fixed
 * delay between requests, backoff on 429/5xx, and a descriptive User-Agent.
 */

import { writeFileSync, mkdirSync, existsSync, readFileSync, appendFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, v] = a.replace(/^--/, "").split("=");
    return [k, v ?? "true"];
  }),
);

const IN_PATH = resolve(__dirname, args.in ?? "data/openfoodfacts-india.jsonl");
const OUT_PATH = resolve(__dirname, args.out ?? "data/refetch.jsonl");
const PROGRESS_PATH = resolve(__dirname, "data/refetch.progress.json");
const DELAY_MS = Number(args.delay ?? 1500);
const MAX_RETRIES = Number(args.retries ?? 6);
const RESUME = args.resume !== "false";
const MERGE = args.merge === "true";
const USER_AGENT = "FamilyFoodIntelligence-DevCatalogueSeed/1.0 (+contact: mohanraj.sc@gmail.com)";

const FIELDS = [
  "code",
  "product_name",
  "brands",
  "categories_tags",
  "quantity",
  "product_quantity",
  "product_quantity_unit",
  "ingredients_text",
  "allergens_tags",
  "nutriments",
  "nutrition_data_per",
  "countries_tags",
  "image_url",
  "image_front_url",
  "nova_group",
  "additives_tags",
  "ingredients_analysis_tags",
  "serving_size",
  "serving_quantity",
  "labels_tags",
  "nutriscore_grade",
].join(",");

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

function readJsonl(path) {
  if (!existsSync(path)) return [];
  return readFileSync(path, "utf8")
    .split("\n")
    .filter(Boolean)
    .flatMap((line) => {
      try {
        return [JSON.parse(line)];
      } catch {
        return []; // skip a corrupt line rather than aborting
      }
    });
}

async function fetchProduct(code) {
  const url = `https://world.openfoodfacts.org/api/v2/product/${encodeURIComponent(code)}?fields=${FIELDS}`;
  for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
    try {
      const res = await fetch(url, { headers: { "User-Agent": USER_AGENT } });
      if (res.status === 404) {
        return { status: "missing" };
      }
      if (res.ok) {
        const body = await res.json();
        if (body.status === 1 && body.product) {
          return { status: "ok", product: body.product };
        }
        return { status: "missing" };
      }
      // 429 / 5xx / 401 (OFF rate-limits with 401 at times): back off and retry.
      const backoff = Math.min(2000 * 2 ** (attempt - 1), 30000);
      console.warn(`  ${code}: HTTP ${res.status}, retry ${attempt}/${MAX_RETRIES} in ${backoff}ms`);
      await sleep(backoff);
    } catch (err) {
      const backoff = Math.min(2000 * 2 ** (attempt - 1), 30000);
      console.warn(`  ${code}: ${err.message}, retry ${attempt}/${MAX_RETRIES} in ${backoff}ms`);
      await sleep(backoff);
    }
  }
  return { status: "failed" };
}

function loadProgress() {
  if (RESUME && existsSync(PROGRESS_PATH)) {
    try {
      return JSON.parse(readFileSync(PROGRESS_PATH, "utf8"));
    } catch {
      // fall through to a fresh start
    }
  }
  return { done: {}, missing: [], failed: [] };
}

function saveProgress(progress) {
  writeFileSync(PROGRESS_PATH, JSON.stringify(progress, null, 0), "utf8");
}

function merge(original, refetched) {
  const byCode = new Map(refetched.map((p) => [p.code, p]));
  let replaced = 0;
  const merged = original.map((p) => {
    const fresh = byCode.get(p.code);
    if (!fresh) return p;
    replaced++;
    // Fields the fresh row carries win; fields only the old row has are kept (never lose data).
    return { ...p, ...fresh };
  });
  return { merged, replaced };
}

async function main() {
  mkdirSync(dirname(OUT_PATH), { recursive: true });
  const original = readJsonl(IN_PATH);
  if (original.length === 0) {
    console.error(`No rows in ${IN_PATH} — run fetch-openfoodfacts.mjs first.`);
    process.exit(1);
  }
  const codes = original.map((p) => p.code).filter((c) => /^[0-9]{8,14}$/.test(c));

  const progress = loadProgress();
  const alreadyDone = new Set(Object.keys(progress.done));
  const pending = codes.filter((c) => !alreadyDone.has(c));
  console.log(`Re-fetching ${pending.length} of ${codes.length} products (${alreadyDone.size} already done)...`);

  if (!RESUME || !existsSync(OUT_PATH)) {
    writeFileSync(OUT_PATH, "", "utf8");
  }

  let fetched = 0;
  let missing = 0;
  let failed = 0;
  try {
    for (const code of pending) {
      const result = await fetchProduct(code);
      if (result.status === "ok") {
        appendFileSync(OUT_PATH, JSON.stringify(result.product) + "\n", "utf8");
        progress.done[code] = 1;
        fetched++;
      } else if (result.status === "missing") {
        progress.done[code] = 0;
        progress.missing.push(code);
        missing++;
      } else {
        progress.failed.push(code);
        failed++;
      }
      const total = fetched + missing + failed;
      if (total % 25 === 0) {
        saveProgress(progress);
        console.log(`  ${total}/${pending.length}: fetched ${fetched}, missing ${missing}, failed ${failed}`);
      }
      await sleep(DELAY_MS);
    }
  } finally {
    saveProgress(progress);
  }

  console.log(`\nRe-fetch complete: fetched ${fetched}, missing ${missing}, failed ${failed} → ${OUT_PATH}`);
  if (failed > 0) {
    console.warn(`  ${failed} products failed after retries; re-run with --resume=true to retry them.`);
  }

  if (MERGE) {
    const refetched = readJsonl(OUT_PATH);
    const { merged, replaced } = merge(original, refetched);
    writeFileSync(IN_PATH, merged.map((p) => JSON.stringify(p)).join("\n") + "\n", "utf8");
    console.log(`Merged ${replaced} re-fetched rows into ${IN_PATH} (${merged.length} rows total).`);
    console.log("Copy it into backend/src/main/resources/db/seed/ and run the seed-catalog-enrich profile.");
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
