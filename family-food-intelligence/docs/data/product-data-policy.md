# Product Data Policy

**Migrated from the original engineering constitution** (`CLAUDE.md` v1, §15/16/17/18/53/54/77/78/79). Verbatim content, reorganized. Operational sourcing procedure: `docs/engineering/repository-assessment.md` §3c and D-016. Binding decisions: D-009, D-010, D-014, D-015.

---

## Product Data

Product data is a core system capability. Maintain canonical entities such as: `Product · ProductVariant · ProductVersion · Brand · Category · Barcode · Ingredient · Nutrient · NutritionPanel · ServingSize · Allergen · EvidenceSource · ProductSource · OCRResult · DataCapture · DataVerification · DataQuality · ChangeHistory`.

Product formulations can change. Do not overwrite historical product information blindly. Support:

```text
Product
   |
   ├── Version 1
   ├── Version 2
   └── Version 3
```

Every important product fact should have provenance. At minimum track: source · capture method · capture/update date · confidence · verification status · product version.

---

## Data Provenance

Never silently invent product data. Every external or user-provided product fact must have a known origin. Acceptable source classes: official regulatory/public sources · authoritative scientific sources · manufacturer-provided information · permitted public/open databases · manually captured package information · reviewed user-submitted label information. Do not scrape websites against explicit technical restrictions or terms simply to increase coverage. When product information is uncertain, say so.

---

## Product Data Freshness

Food formulations change. The system should support identifying stale data. Possible states: `VERIFIED · RECENT · STALE · CONFLICTING · NEEDS_REVIEW · UNVERIFIED`. When relevant, expose data freshness to users. Never present old data as definitively current when the system knows it may be stale.

---

## OCR / Label Processing

OCR is a data extraction mechanism, not the source of truth.

```text
Image → OCR → Extraction → Normalization → Validation → Structured Product Data → Scoring
```

Handle: poor lighting · rotated labels · blur · multiple languages · unusual units · table layouts · partial labels · missing values · OCR errors. If confidence is low, ask for another image or confirmation. Do not hallucinate missing nutrient values. (AI boundaries for the OCR adapter itself: `docs/ai/ai-policy.md`.)

---

## Product Score Versioning

Score methodology must be versioned. Store `score_version · score_timestamp · score_inputs`. When the scoring methodology changes: document why, update tests, update methodology, assess historical impact. Do not silently change the meaning of an existing score.

---

## Evidence Hierarchy

Preferred evidence, most to least authoritative:

1. government/regulator/official sources
2. high-quality systematic reviews/meta-analyses
3. major scientific literature
4. professional guidance
5. credible secondary analysis
6. emerging/limited evidence

Every material health/nutrition claim should have source/provenance. Never present weak evidence as definitive.

---

## Product Data Quality Rules

Before using product data in scoring, validate: units · nutrient ranges · serving size · required fields · impossible values · duplicate identifiers · conflicting values. Examples of invalid conditions: negative calories · impossible macronutrient totals · malformed units · impossible serving sizes. Do not let invalid data enter the scoring engine silently.

---

## Data Confidence

Where useful, represent `HIGH CONFIDENCE · MEDIUM CONFIDENCE · LOW CONFIDENCE`, based on source quality, recency, completeness, verification, OCR confidence, conflicts. Do not expose an artificial numerical confidence merely to look sophisticated.

---

## User-Submitted Data

When users submit product labels, treat it as:

```text
Unverified Input → Validation → Review → Canonical Product Data
```

Do not automatically treat a user-uploaded value as authoritative if it materially affects safety/recommendations.
