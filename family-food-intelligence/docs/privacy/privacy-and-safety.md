# Privacy and Safety

**Migrated from the original engineering constitution** (`CLAUDE.md` v1, §11/12/13/31/33/52). Verbatim content, reorganized. Consent/child-processing mechanism decisions: `docs/decisions/decision-log.md` D-001, D-002. Access-control enforcement: `docs/security/security-policy.md`.

---

## Child Profile Policy

Children are a supported product use case. A child must be treated as a **parent/guardian-controlled household member**, not as an independent consumer account.

The application must support: guardian relationship · consent state · consent version · consent timestamp · purpose of processing · data minimization · appropriate access control · deletion capability · auditability.

Do not implement child-directed: advertising · targeted marketing · public profiles · social features · leaderboards · weight-loss competitions · body-image scoring · calorie restriction challenges · behavioral gamification · engagement mechanisms intended to manipulate children.

Never collect child data merely because it is technically convenient. Collect only what is necessary for the stated product purpose.

---

## Child Nutrition Safety

For children, focus on appropriate food/nutrition guidance such as: dietary diversity · protein · fiber · fruit/vegetable consumption · excessive added sugar · sodium exposure · allergens · balanced food choices.

Do NOT make unsupported statements such as "Your child is deficient," "Your child must lose weight," or "This food will prevent disease."

Prefer: "The recorded diet appears low in…" and "Consider discussing nutritional adequacy with a qualified healthcare professional."

---

## Medical Safety

The application is a food/nutrition decision-support product. It is NOT a diagnostic system, a treatment system, a medication management system, or a doctor replacement.

Never: diagnose a disease · prescribe treatment · recommend medication changes · claim to detect nutritional deficiency from meal logs alone · tell a user to stop prescribed treatment · invent clinical thresholds · imply clinical certainty where none exists.

Prefer: "High in added sugar." · "This may not fit your current dietary goal." · "Based on the profile information you provided…" · "Consider discussing this with a qualified healthcare professional."

---

## Nutrition Data Limitations

Do not imply that food logs alone can prove nutrient deficiency, disease, treatment response, or clinical nutritional status. For home-cooked meals, nutrition estimates may have uncertainty because ingredients, preparation methods, portion size, and cooking quantities vary. Represent uncertainty honestly.

---

## Privacy

Treat household health, dietary, allergy, nutrition, and child-related information as sensitive. Implement: authentication · authorization · least privilege · household isolation · secure storage · encryption in transit · appropriate encryption at rest · secrets management · audit logging · deletion · data-access/export flows where required · consent tracking · privacy-safe analytics.

Never log: passwords · authentication tokens · sensitive child data · unnecessary health details · secrets.

---

## Child Data Access

Child records require stricter access controls. A non-guardian household member must not automatically receive unrestricted access to child data unless explicitly authorized by the product model.

Audit: creation · consent · edits · access · export · deletion · guardian changes.
