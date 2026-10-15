# Source 006 — NOVA food classification (Monteiro et al., FAO 2019)

**Publisher:** Food and Agriculture Organization of the United Nations (FAO), Rome
**Title:** *Ultra-processed foods, diet quality, and health using the NOVA classification system*
**Authors:** Carlos Augusto Monteiro, Geoffrey Cannon, Mark Lawrence, Maria Laura da Costa Louzada, Priscila Pereira Machado
**Year:** 2019
**ISBN:** 978-92-5-131701-3
**Licence:** CC BY-NC-SA 3.0 IGO
**Required citation (per the document itself):** Monteiro, C.A., Cannon, G., Lawrence, M., Costa Louzada, M.L. and Pereira Machado, P. 2019. *Ultra-processed foods, diet quality, and health using the NOVA classification system.* Rome, FAO.
**Document URI:** `https://openknowledge.fao.org/server/api/core/bitstreams/5277b379-0acb-4d97-a6a3-602774104629/content`
**Retrieved:** 2026-09-13 (fetched with a browser user-agent; the bare URL returns HTTP 403 to an unidentified client)

This is a **classification system**, not a numeric threshold — it defines four groups by the nature and purpose of processing, not by a gram or percentage cut-off. It is cited here only as the definitional basis for a possible `NOVA_GROUP` fact/rule; the Open Food Facts import already carries a per-product `nova-group` field (catalogue enrichment, Increment 1) which is validated (1–4 only) before use, not derived by this project.

## The four groups (quoted verbatim, pages 5–9)

> **Group 1 — Unprocessed and minimally processed foods.** "Unprocessed (or natural) foods are the edible parts of plants (such as fruit, leaves, stems, seeds, roots) or from animals (such as muscle, offal, eggs, milk), and also fungi, algae and water, after separation from nature... Minimally processed foods are natural foods altered by methods that include removal of inedible or unwanted parts, and also processes that include drying, crushing, grinding, powdering, fractioning, filtering, roasting, boiling, non-alcoholic fermentation, pasteurization, chilling, freezing, placing in containers, and vacuum packaging. The distinction between unprocessed and minimally processed foods is not especially significant."

> **Group 2 — Processed culinary ingredients.** "Processed culinary ingredients include oils, butter, lard, sugar and salt... substances derived from group 1 foods or else from nature by processes such as pressing, refining, grinding, milling, and drying... In isolation, processed culinary ingredients are unbalanced, being depleted in some or most nutrients... they are rarely if ever consumed by themselves. They are used in combination with foods to make palatable, diverse, nourishing and enjoyable meals and dishes."

> **Group 3 — Processed foods.** "These include canned or bottled vegetables or legumes (pulses) preserved in brine; whole fruit preserved in syrup; tinned fish preserved in oil; some types of processed animal foods such as ham, bacon, pastrami, and smoked fish; most freshly baked breads; and simple cheeses to which salt is added... They are made by adding salt, oil, sugar or other substances from group 2 to group 1 foods... Most processed foods have two or three ingredients, and are recognisable as modified versions of group 1 foods."

> **Group 4 — Ultra-processed foods.** "Ultra-processed foods are formulations of ingredients, mostly of exclusive industrial use, typically created by series of industrial techniques and processes (hence 'ultra-processed')... Some common ultra-processed products are carbonated soft drinks; sweet, fatty or salty packaged snacks; candies (confectionery); mass produced packaged breads and buns, cookies (biscuits), pastries, cakes and cake mixes; margarine and other spreads; sweetened breakfast 'cereals' and fruit yoghurt and 'energy' drinks; pre-prepared meat, cheese, pasta and pizza dishes; poultry and fish 'nuggets' and 'sticks'; sausages, burgers, hot dogs and other reconstituted meat products; powdered and packaged 'instant' soups, noodles and desserts; baby formula; and many other types of product... Ingredients characteristic of ultra-processed foods are either food substances of no or rare culinary use [varieties of sugars, modified oils, isolated proteins], or else classes of additives whose function is to make the final product sellable, palatable and often hyper-palatable [flavours, flavour enhancers, colours, emulsifiers, artificial sweeteners, thickeners, and foaming/anti-foaming/bulking/carbonating/gelling/glazing agents]."

## Scope note for this project

Several of this project's own 13 categories (BISCUITS, CHIPS, INSTANT_NOODLES, CHOCOLATE, most BEVERAGES, MALT_DRINK) are explicitly named by the source document as typical Group 4 examples ("mass produced packaged... cookies (biscuits)... sweetened breakfast 'cereals'... powdered and packaged 'instant'... noodles"). This is offered to the reviewer as context, not as a numeric rule — the classification itself carries no severity gradient beyond "which of four groups," and `rules-v2.md`'s optional `NOVA_GROUP = 4` rule is the only place this source is proposed to be used, gated on the reviewer's approval.
