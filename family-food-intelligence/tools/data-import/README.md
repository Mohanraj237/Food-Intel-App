# Development catalogue import (Open Food Facts)

Populates the local/dev database with a real, honestly-sourced product catalogue from
[Open Food Facts](https://world.openfoodfacts.org) — the source `docs/architecture/system-architecture.md`
already names for this. See `docs/decisions/decision-log.md` D-031 for the full decision
record (source, licensing, honesty commitments) and D-033 for how `recommendation` uses this
data without inventing a nutrition judgment.

This is a two-step, offline process. Neither step is part of the running application.

## 1. Fetch

```bash
cd tools/data-import
node fetch-openfoodfacts.mjs --target=6000
```

Pulls India-market products from the public Open Food Facts search API (rate-limited,
retried with backoff — see the script's own comments) into
`tools/data-import/data/openfoodfacts-india.jsonl` (gitignored — regenerate it, don't commit
it from here; the copy that ships with the app lives at
`backend/src/main/resources/db/seed/openfoodfacts-india.jsonl`).

If Open Food Facts is degraded or rate-limiting mid-run (it happens — this is a
community-run nonprofit service, not a CDN), the script saves whatever it already has
rather than losing progress. Resume a later attempt with:

```bash
node fetch-openfoodfacts.mjs --target=6000 --resume=true --start-page=<N>
```

`<N>` should be a bit before wherever the previous run's log stopped making progress.

Once you have a snapshot you're happy with, copy it into the backend's resources so the
importer (a Spring Boot component, not a separate script) can find it:

```bash
cp data/openfoodfacts-india.jsonl ../../backend/src/main/resources/db/seed/openfoodfacts-india.jsonl
```

## 2. Import

From `backend/`, boot the app with the `seed-catalog` profile added (never active by
default — see `catalog.seed.OpenFoodFactsSeedRunner`'s own Javadoc):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local,seed-catalog
```

The importer logs a summary when it finishes (rows read, products imported, skipped,
with-nutrition/with-ingredients/with-allergens counts, category and data-quality
breakdowns). Every imported product goes through `catalog.application.ProductIngestionService`
— the same write path ops-review contribution approval uses — so D-014 versioning and D-015
per-field provenance are honoured, and every version is `NEEDS_VERIFICATION` / `RECENT`-or-
`UNVERIFIED` data quality, never `VERIFIED` (nobody at this project has checked these rows).

**Idempotent.** Re-running against a database that already has some of these products skips
any GTIN that already has an active barcode mapping — safe to re-run after fetching a larger
snapshot, or after a partial run.

## 3. Backfill images (optional, separate step)

The snapshot's `image_url`/`image_front_url` fields (fetched since this step was added — a
re-run of step 1 is needed for an older snapshot) point at Open Food Facts' own image
hosting. This step downloads each product's image once and stores it in this application's
own catalogue-image bucket (`familyfood.storage.catalogue-bucket`) — the running app never
hotlinks a user's browser to `images.openfoodfacts.org`. Deliberately a separate profile
from step 2, since it makes a live network call per product against a source that has had
sustained availability problems for this project (see `docs/decisions/decision-log.md`
D-031):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local,seed-catalog-images
```

**Idempotent and resumable.** A product that already has an image is skipped without a
network call, so re-running after a partial run or an Open Food Facts outage only tops up
what's missing. See `catalog.seed.OpenFoodFactsImageBackfillRunner`'s own doc comment for
the full behaviour, including what happens when Open Food Facts cannot be reached at all
(the run completes and reports an honest zero, rather than failing or fabricating a
placeholder image).

## Verify the count

```bash
curl -s "http://localhost:8090/api/v1/search?size=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['total'])"
```

or directly against Postgres:

```bash
docker exec ffi-postgres psql -U familyfood -d familyfood -c "SELECT count(*) FROM product;"
```

## Reset the development catalogue

Dev/test data only — never run against anything but the local `ffi-postgres` container.

```bash
docker exec ffi-postgres psql -U familyfood -d familyfood -c "TRUNCATE product CASCADE;"
```

`TRUNCATE ... CASCADE` follows every `ON DELETE CASCADE` foreign key from `product`
(`product_version`, `product_source`, `product_nutrient`, `product_ingredient`,
`product_allergen`, `product_barcode`, `product_conflict`, and a few others) — it does not
touch `brand` or `category` (reference data), so re-importing afterward reuses existing
brand rows rather than creating duplicates.
