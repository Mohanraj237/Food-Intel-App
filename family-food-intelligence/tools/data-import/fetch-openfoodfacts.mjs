#!/usr/bin/env node
/**
 * Fetches a real, honestly-sourced product dataset for India from the Open
 * Food Facts public API (https://world.openfoodfacts.org), which the
 * project's own architecture doc names as the intended catalogue import
 * source (docs/architecture/system-architecture.md §2, "Open Food Facts
 * (import, contribute)"; SourceType.OPEN_FOOD_FACTS and
 * CaptureMethod.OFF_IMPORT already exist in the schema for exactly this).
 *
 * Open Food Facts data is licensed under the Open Database License (ODbL)
 * 1.0 — https://opendatacommons.org/licenses/odbl/1.0/. Attribution is
 * recorded per-row in `product_source.license_note` (see
 * ProductIngestionService) and must also appear in the product UI/footer —
 * see docs/decisions/decision-log.md for the decision recording this import.
 *
 * This is an OFFLINE, one-time (or occasionally re-run) data-acquisition
 * step — never a runtime dependency of the application. It writes a JSONL
 * snapshot that backend/.../catalog/seed/OpenFoodFactsSeedRunner.java reads
 * to populate the development database. Nothing here talks to Postgres.
 *
 * Usage:
 *   node fetch-openfoodfacts.mjs [--target=6500] [--out=<path>]
 *
 * Respectful-use notes (this is a shared community API, not a CDN):
 *   - Sends a descriptive User-Agent identifying this project + contact.
 *   - Requests only the fields actually needed (fields=... param).
 *   - Serialises requests with a fixed delay and retries 503s with backoff
 *     instead of hammering on failure.
 */

import { writeFileSync, mkdirSync, existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, v] = a.replace(/^--/, "").split("=");
    return [k, v ?? "true"];
  }),
);

const TARGET = Number(args.target ?? 6500);
const PAGE_SIZE = Number(args["page-size"] ?? 100);
const OUT_PATH = resolve(__dirname, args.out ?? "data/openfoodfacts-india.jsonl");
const DELAY_MS = Number(args.delay ?? 1500);
const MAX_RETRIES = Number(args.retries ?? 6);
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
].join(",");

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function fetchPage(page) {
  const url =
    `https://world.openfoodfacts.org/api/v2/search` +
    `?countries_tags=en:india&page_size=${PAGE_SIZE}&page=${page}&fields=${FIELDS}`;

  for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
    try {
      const res = await fetch(url, { headers: { "User-Agent": USER_AGENT } });
      if (res.ok) {
        return res.json();
      }
      const backoff = Math.min(2000 * 2 ** (attempt - 1), 20000);
      console.warn(
        `  page ${page}: HTTP ${res.status}, retry ${attempt}/${MAX_RETRIES} in ${backoff}ms`,
      );
      await sleep(backoff);
    } catch (err) {
      const backoff = Math.min(2000 * 2 ** (attempt - 1), 20000);
      console.warn(
        `  page ${page}: ${err.message}, retry ${attempt}/${MAX_RETRIES} in ${backoff}ms`,
      );
      await sleep(backoff);
    }
  }
  // Give up on this one page rather than aborting the whole run — pagination is
  // stable, so skipping a page just costs ~PAGE_SIZE candidate products, never
  // correctness. A transient outage on one page must not lose everything already
  // fetched.
  console.warn(`  page ${page}: giving up after ${MAX_RETRIES} retries, skipping`);
  return { products: [] };
}

function isUsable(p) {
  // Minimum bar: a real name and a real barcode. Everything else (nutrition,
  // ingredients, allergens) is optional and imported as-present — absence is
  // recorded honestly downstream, never backfilled with an invented value.
  return Boolean(p.code && /^[0-9]{8,14}$/.test(p.code) && p.product_name && p.product_name.trim());
}

function save(out) {
  const lines = out.map((p) => JSON.stringify(p));
  writeFileSync(OUT_PATH, lines.join("\n") + "\n", "utf8");
}

async function main() {
  mkdirSync(dirname(OUT_PATH), { recursive: true });

  const seenCodes = new Set();
  const out = [];

  // --resume picks up an existing snapshot (this file, by default) instead of
  // starting over — useful when a prior run stopped partway through an Open
  // Food Facts outage. --start-page skips straight to a later page, since the
  // early pages are the ones already proven to succeed.
  if (args.resume === "true" && existsSync(OUT_PATH)) {
    const existing = readFileSync(OUT_PATH, "utf8").split("\n").filter(Boolean);
    for (const line of existing) {
      try {
        const p = JSON.parse(line);
        if (p.code && !seenCodes.has(p.code)) {
          seenCodes.add(p.code);
          out.push(p);
        }
      } catch {
        // skip a corrupt line rather than aborting a resume
      }
    }
    console.log(`Resumed ${out.length} products from ${OUT_PATH}`);
  }

  let page = Number(args["start-page"] ?? 1);
  let consecutiveEmpty = 0;

  console.log(`Fetching Open Food Facts India products (target: ${TARGET} usable rows)...`);

  try {
    while (out.length < TARGET && consecutiveEmpty < 8) {
      const data = await fetchPage(page);
      const products = Array.isArray(data.products) ? data.products : [];

      if (products.length === 0) {
        consecutiveEmpty++;
      } else {
        consecutiveEmpty = 0;
      }

      let addedThisPage = 0;
      for (const p of products) {
        if (!isUsable(p) || seenCodes.has(p.code)) continue;
        seenCodes.add(p.code);
        out.push(p);
        addedThisPage++;
      }

      console.log(
        `  page ${page}: fetched ${products.length}, usable+new ${addedThisPage}, total ${out.length}/${TARGET}`,
      );

      page++;
      if (page % 10 === 0) {
        save(out); // checkpoint, so a later crash never loses everything fetched so far
      }
      if (page > 400) {
        console.warn("Safety cap of 400 pages reached; stopping.");
        break;
      }
      await sleep(DELAY_MS);
    }
  } finally {
    save(out);
  }

  console.log(`\nWrote ${out.length} products to ${OUT_PATH}`);
  if (out.length < TARGET) {
    console.warn(
      `WARNING: only reached ${out.length} of the ${TARGET} target (Open Food Facts' ` +
        `India-tagged catalogue or transient API errors limited this run).`,
    );
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
