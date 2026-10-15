-- =============================================================================
-- V15  Catalogue enrichment (Increment 1, D-059 — schema half; 1b's image
--      gallery columns are included here so 1b needs no migration of its own)
--
-- Adds the catalogue facts the v2 scoring engine and search v2 read that the
-- Open Food Facts import did not carry before:
--
--   product_version.nova_group / diet_flag   processing level (NOVA 1-4) and an
--                                            estimated vegetarian/vegan/non-veg
--                                            flag, each with its own provenance
--                                            row (D-015), never overwritten
--                                            (D-014: enrichment fills NULL -> value
--                                            only, through catalog.application).
--   additive                                 INS reference vocabulary (Codex
--                                            CXG 36-1989 names and functional
--                                            classes only — no health claim of
--                                            any kind attaches to a row here).
--   product_ingredient.additive_code         which additive a label token is.
--   category_alias                           what people type -> category code.
--   unknown_barcode                          scans that hit nothing (no account
--                                            id: a GTIN is not personal data).
--   nutrient ENERGY_KJ, FVNL_PCT             two more nutrient codes the OFF rows
--                                            carry; FVNL_PCT is always imported
--                                            with is_estimated = true.
--   brand_alias seeds                        the common misspellings/short forms
--                                            for brands already in the catalogue.
--   product_image gallery columns            purpose, ordering, derivation and
--                                            licence for Increment 1b.
--
-- Nothing here changes an existing fact. Every new column is nullable or has a
-- default that means "not known", and the enrichment code path that fills them
-- records a product_source and a product_change_log row per fill.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Nutrient reference rows
-- -----------------------------------------------------------------------------
INSERT INTO nutrient (code, display_name, canonical_unit, sort_order, is_core) VALUES
    ('ENERGY_KJ', 'Energy (kJ)', 'kJ', 15, false),
    ('FVNL_PCT',  'Fruit, vegetable, nut and legume content', '%', 150, false)
ON CONFLICT (code) DO NOTHING;

COMMENT ON COLUMN nutrient.code IS
    'FVNL_PCT is only ever an estimate derived from the ingredient list (Open Food '
    'Facts fruits-vegetables-nuts-estimate-from-ingredients_100g); rows with this '
    'code must carry is_estimated = true and the UI must say so.';


-- -----------------------------------------------------------------------------
-- product_version: processing level and diet flag
-- -----------------------------------------------------------------------------
-- The two *_source_id foreign keys are DEFERRABLE INITIALLY DEFERRED: a version and the
-- product_source row its facts point at reference each other (product_source.
-- product_version_id -> product_version), so within one ingest transaction neither can
-- be inserted strictly first. The check still runs, at commit.
ALTER TABLE product_version
    ADD COLUMN nova_group          smallint
                                     CHECK (nova_group IS NULL OR nova_group BETWEEN 1 AND 4),
    ADD COLUMN nova_source_id      uuid,
    ADD COLUMN diet_flag           text NOT NULL DEFAULT 'UNKNOWN'
                                     CHECK (diet_flag IN ('VEG','NON_VEG','VEGAN','UNKNOWN')),
    ADD COLUMN diet_flag_estimated boolean NOT NULL DEFAULT true,
    ADD COLUMN diet_source_id      uuid;

ALTER TABLE product_version
    ADD CONSTRAINT fk_version_nova_source FOREIGN KEY (nova_source_id)
        REFERENCES product_source(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_version_diet_source FOREIGN KEY (diet_source_id)
        REFERENCES product_source(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;

COMMENT ON COLUMN product_version.nova_group IS
    'NOVA processing group 1-4 (Monteiro et al.; FAO 2019). Never a score weight '
    'unless a reviewed rule set adds one (D-059). NULL = not classified.';
COMMENT ON COLUMN product_version.diet_flag IS
    'VEG/VEGAN/NON_VEG as declared or estimated from the ingredient list. '
    'diet_flag_estimated = true means it is an estimate, not the pack''s FSSAI '
    'green/brown mark, and the UI must say so.';


-- -----------------------------------------------------------------------------
-- Additive reference vocabulary
--
-- Source: Codex Alimentarius, "Class Names and the International Numbering
-- System for Food Additives", CXG 36-1989 (rev. 2021) —
-- https://www.fao.org/fao-who-codexalimentarius/codex-texts/guidelines/en/
-- FSSAI's Food Products Standards and Food Additives Regulations, 2011,
-- Appendix A, uses the same INS numbering. Only the name and the Codex
-- functional class are recorded. This table makes no statement about safety,
-- permitted use or health effect — it exists so a label token such as
-- "INS 211" can be shown with its name, nothing more.
-- -----------------------------------------------------------------------------
CREATE TABLE additive (
    ins_number        text PRIMARY KEY,
    e_number          text,
    name              text NOT NULL,
    function_category text,
    source_ref        text NOT NULL
);

CREATE UNIQUE INDEX uq_additive_e_number ON additive (e_number) WHERE e_number IS NOT NULL;

INSERT INTO additive (ins_number, e_number, name, function_category, source_ref) VALUES
    -- Colours
    ('100',     'E100',  'Curcumin',                                   'Colour',                     'Codex CXG 36-1989'),
    ('101',     'E101',  'Riboflavins',                                'Colour',                     'Codex CXG 36-1989'),
    ('102',     'E102',  'Tartrazine',                                 'Colour',                     'Codex CXG 36-1989'),
    ('110',     'E110',  'Sunset yellow FCF',                          'Colour',                     'Codex CXG 36-1989'),
    ('120',     'E120',  'Carmines',                                   'Colour',                     'Codex CXG 36-1989'),
    ('122',     'E122',  'Azorubine (carmoisine)',                     'Colour',                     'Codex CXG 36-1989'),
    ('124',     'E124',  'Ponceau 4R',                                 'Colour',                     'Codex CXG 36-1989'),
    ('127',     'E127',  'Erythrosine',                                'Colour',                     'Codex CXG 36-1989'),
    ('129',     'E129',  'Allura red AC',                              'Colour',                     'Codex CXG 36-1989'),
    ('132',     'E132',  'Indigotine (indigo carmine)',                'Colour',                     'Codex CXG 36-1989'),
    ('133',     'E133',  'Brilliant blue FCF',                         'Colour',                     'Codex CXG 36-1989'),
    ('140',     'E140',  'Chlorophylls',                               'Colour',                     'Codex CXG 36-1989'),
    ('141',     'E141',  'Chlorophylls and chlorophyllins, copper complexes', 'Colour',              'Codex CXG 36-1989'),
    ('142',     'E142',  'Green S',                                    'Colour',                     'Codex CXG 36-1989'),
    ('150a',    'E150a', 'Caramel I – plain caramel',                  'Colour',                     'Codex CXG 36-1989'),
    ('150b',    'E150b', 'Caramel II – sulfite caramel',               'Colour',                     'Codex CXG 36-1989'),
    ('150c',    'E150c', 'Caramel III – ammonia caramel',              'Colour',                     'Codex CXG 36-1989'),
    ('150d',    'E150d', 'Caramel IV – sulfite ammonia caramel',       'Colour',                     'Codex CXG 36-1989'),
    ('160a',    'E160a', 'Carotenes',                                  'Colour',                     'Codex CXG 36-1989'),
    ('160b',    'E160b', 'Annatto extracts',                           'Colour',                     'Codex CXG 36-1989'),
    ('160c',    'E160c', 'Paprika extract / paprika oleoresin',        'Colour',                     'Codex CXG 36-1989'),
    ('162',     'E162',  'Beet red',                                   'Colour',                     'Codex CXG 36-1989'),
    ('163',     'E163',  'Anthocyanins',                               'Colour',                     'Codex CXG 36-1989'),
    ('170(i)',  'E170',  'Calcium carbonate',                          'Colour / anticaking agent',  'Codex CXG 36-1989'),
    ('171',     'E171',  'Titanium dioxide',                           'Colour',                     'Codex CXG 36-1989'),
    ('172',     'E172',  'Iron oxides',                                'Colour',                     'Codex CXG 36-1989'),
    -- Preservatives
    ('200',     'E200',  'Sorbic acid',                                'Preservative',               'Codex CXG 36-1989'),
    ('201',     'E201',  'Sodium sorbate',                             'Preservative',               'Codex CXG 36-1989'),
    ('202',     'E202',  'Potassium sorbate',                          'Preservative',               'Codex CXG 36-1989'),
    ('203',     'E203',  'Calcium sorbate',                            'Preservative',               'Codex CXG 36-1989'),
    ('210',     'E210',  'Benzoic acid',                               'Preservative',               'Codex CXG 36-1989'),
    ('211',     'E211',  'Sodium benzoate',                            'Preservative',               'Codex CXG 36-1989'),
    ('212',     'E212',  'Potassium benzoate',                         'Preservative',               'Codex CXG 36-1989'),
    ('220',     'E220',  'Sulfur dioxide',                             'Preservative / antioxidant', 'Codex CXG 36-1989'),
    ('221',     'E221',  'Sodium sulfite',                             'Preservative / antioxidant', 'Codex CXG 36-1989'),
    ('222',     'E222',  'Sodium hydrogen sulfite',                    'Preservative / antioxidant', 'Codex CXG 36-1989'),
    ('223',     'E223',  'Sodium metabisulfite',                       'Preservative / antioxidant', 'Codex CXG 36-1989'),
    ('224',     'E224',  'Potassium metabisulfite',                    'Preservative / antioxidant', 'Codex CXG 36-1989'),
    ('234',     'E234',  'Nisin',                                      'Preservative',               'Codex CXG 36-1989'),
    ('250',     'E250',  'Sodium nitrite',                             'Preservative / colour retention agent', 'Codex CXG 36-1989'),
    ('251',     'E251',  'Sodium nitrate',                             'Preservative / colour retention agent', 'Codex CXG 36-1989'),
    ('260',     'E260',  'Acetic acid, glacial',                       'Acidity regulator / preservative', 'Codex CXG 36-1989'),
    ('262',     'E262',  'Sodium acetates',                            'Acidity regulator / preservative', 'Codex CXG 36-1989'),
    ('270',     'E270',  'Lactic acid (L-, D- and DL-)',               'Acidity regulator',          'Codex CXG 36-1989'),
    ('280',     'E280',  'Propionic acid',                             'Preservative',               'Codex CXG 36-1989'),
    ('281',     'E281',  'Sodium propionate',                          'Preservative',               'Codex CXG 36-1989'),
    ('282',     'E282',  'Calcium propionate',                         'Preservative',               'Codex CXG 36-1989'),
    ('290',     'E290',  'Carbon dioxide',                             'Carbonating agent / packaging gas', 'Codex CXG 36-1989'),
    ('296',     'E296',  'Malic acid (DL-)',                           'Acidity regulator',          'Codex CXG 36-1989'),
    ('297',     'E297',  'Fumaric acid',                               'Acidity regulator',          'Codex CXG 36-1989'),
    -- Antioxidants
    ('300',     'E300',  'Ascorbic acid, L-',                          'Antioxidant',                'Codex CXG 36-1989'),
    ('301',     'E301',  'Sodium ascorbate',                           'Antioxidant',                'Codex CXG 36-1989'),
    ('306',     'E306',  'Tocopherol-rich extract (mixed tocopherols)', 'Antioxidant',               'Codex CXG 36-1989'),
    ('307a',    'E307a', 'd-alpha-Tocopherol',                         'Antioxidant',                'Codex CXG 36-1989'),
    ('307b',    'E307b', 'Tocopherol concentrate, mixed',              'Antioxidant',                'Codex CXG 36-1989'),
    ('307c',    'E307c', 'dl-alpha-Tocopherol',                        'Antioxidant',                'Codex CXG 36-1989'),
    ('319',     'E319',  'Tertiary butylhydroquinone (TBHQ)',          'Antioxidant',                'Codex CXG 36-1989'),
    ('320',     'E320',  'Butylated hydroxyanisole (BHA)',             'Antioxidant',                'Codex CXG 36-1989'),
    ('321',     'E321',  'Butylated hydroxytoluene (BHT)',             'Antioxidant',                'Codex CXG 36-1989'),
    ('322',     'E322',  'Lecithins',                                  'Emulsifier / antioxidant',   'Codex CXG 36-1989'),
    ('325',     'E325',  'Sodium lactate',                             'Acidity regulator / humectant', 'Codex CXG 36-1989'),
    ('326',     'E326',  'Potassium lactate',                          'Acidity regulator',          'Codex CXG 36-1989'),
    ('327',     'E327',  'Calcium lactate',                            'Acidity regulator',          'Codex CXG 36-1989'),
    ('330',     'E330',  'Citric acid',                                'Acidity regulator / antioxidant', 'Codex CXG 36-1989'),
    ('331',     'E331',  'Sodium citrates',                            'Acidity regulator / emulsifier', 'Codex CXG 36-1989'),
    ('331(iii)','E331iii','Trisodium citrate',                         'Acidity regulator / emulsifier', 'Codex CXG 36-1989'),
    ('332',     'E332',  'Potassium citrates',                         'Acidity regulator / stabilizer', 'Codex CXG 36-1989'),
    ('333',     'E333',  'Calcium citrates',                           'Acidity regulator / firming agent', 'Codex CXG 36-1989'),
    ('334',     'E334',  'Tartaric acid (L(+)-)',                      'Acidity regulator / antioxidant', 'Codex CXG 36-1989'),
    ('335',     'E335',  'Sodium tartrates',                           'Acidity regulator / stabilizer', 'Codex CXG 36-1989'),
    ('338',     'E338',  'Phosphoric acid',                            'Acidity regulator / antioxidant', 'Codex CXG 36-1989'),
    ('339',     'E339',  'Sodium phosphates',                          'Acidity regulator / emulsifier', 'Codex CXG 36-1989'),
    ('339(i)',  'E339i', 'Monosodium dihydrogen phosphate',            'Acidity regulator / emulsifier', 'Codex CXG 36-1989'),
    ('339(ii)', 'E339ii','Disodium hydrogen phosphate',                'Acidity regulator / emulsifier', 'Codex CXG 36-1989'),
    ('340',     'E340',  'Potassium phosphates',                       'Acidity regulator / emulsifier', 'Codex CXG 36-1989'),
    ('341',     'E341',  'Calcium phosphates',                         'Acidity regulator / raising agent', 'Codex CXG 36-1989'),
    ('341(i)',  'E341i', 'Monocalcium phosphate',                      'Acidity regulator / raising agent', 'Codex CXG 36-1989'),
    ('341(iii)','E341iii','Tricalcium phosphate',                      'Anticaking agent / acidity regulator', 'Codex CXG 36-1989'),
    ('350',     'E350',  'Sodium malates',                             'Acidity regulator',          'Codex CXG 36-1989'),
    ('385',     'E385',  'Calcium disodium ethylenediaminetetraacetate', 'Antioxidant / sequestrant', 'Codex CXG 36-1989'),
    -- Thickeners, gelling agents, stabilizers, emulsifiers
    ('401',     'E401',  'Sodium alginate',                            'Thickener / stabilizer',     'Codex CXG 36-1989'),
    ('405',     'E405',  'Propylene glycol alginate',                  'Thickener / emulsifier',     'Codex CXG 36-1989'),
    ('406',     'E406',  'Agar',                                       'Gelling agent / thickener',  'Codex CXG 36-1989'),
    ('407',     'E407',  'Carrageenan',                                'Thickener / gelling agent',  'Codex CXG 36-1989'),
    ('410',     'E410',  'Carob bean gum (locust bean gum)',           'Thickener / stabilizer',     'Codex CXG 36-1989'),
    ('412',     'E412',  'Guar gum',                                   'Thickener / stabilizer',     'Codex CXG 36-1989'),
    ('413',     'E413',  'Tragacanth gum',                             'Thickener / emulsifier',     'Codex CXG 36-1989'),
    ('414',     'E414',  'Gum arabic (acacia gum)',                    'Thickener / emulsifier',     'Codex CXG 36-1989'),
    ('415',     'E415',  'Xanthan gum',                                'Thickener / stabilizer',     'Codex CXG 36-1989'),
    ('416',     'E416',  'Karaya gum',                                 'Thickener / stabilizer',     'Codex CXG 36-1989'),
    ('417',     'E417',  'Tara gum',                                   'Thickener / stabilizer',     'Codex CXG 36-1989'),
    ('418',     'E418',  'Gellan gum',                                 'Thickener / gelling agent',  'Codex CXG 36-1989'),
    ('420',     'E420',  'Sorbitol',                                   'Sweetener / humectant',      'Codex CXG 36-1989'),
    ('421',     'E421',  'Mannitol',                                   'Sweetener / anticaking agent', 'Codex CXG 36-1989'),
    ('422',     'E422',  'Glycerol',                                   'Humectant',                  'Codex CXG 36-1989'),
    ('428',     'E428',  'Gelatin',                                    'Gelling agent / thickener',  'Codex CXG 36-1989'),
    ('440',     'E440',  'Pectins',                                    'Gelling agent / thickener',  'Codex CXG 36-1989'),
    ('450',     'E450',  'Diphosphates',                               'Raising agent / emulsifier / stabilizer', 'Codex CXG 36-1989'),
    ('450(i)',  'E450i', 'Disodium diphosphate',                       'Raising agent / emulsifier', 'Codex CXG 36-1989'),
    ('450(iii)','E450iii','Tetrasodium diphosphate',                   'Emulsifier / stabilizer',    'Codex CXG 36-1989'),
    ('451',     'E451',  'Triphosphates',                              'Emulsifier / stabilizer',    'Codex CXG 36-1989'),
    ('452',     'E452',  'Polyphosphates',                             'Emulsifier / stabilizer',    'Codex CXG 36-1989'),
    ('460',     'E460',  'Cellulose (microcrystalline / powdered)',    'Anticaking agent / thickener', 'Codex CXG 36-1989'),
    ('461',     'E461',  'Methyl cellulose',                           'Thickener / emulsifier',     'Codex CXG 36-1989'),
    ('464',     'E464',  'Hydroxypropyl methyl cellulose',             'Thickener / emulsifier',     'Codex CXG 36-1989'),
    ('466',     'E466',  'Sodium carboxymethyl cellulose',             'Thickener / stabilizer',     'Codex CXG 36-1989'),
    ('470',     'E470',  'Salts of fatty acids',                       'Anticaking agent / emulsifier', 'Codex CXG 36-1989'),
    ('471',     'E471',  'Mono- and di-glycerides of fatty acids',     'Emulsifier',                 'Codex CXG 36-1989'),
    ('472a',    'E472a', 'Acetic and fatty acid esters of glycerol',   'Emulsifier',                 'Codex CXG 36-1989'),
    ('472b',    'E472b', 'Lactic and fatty acid esters of glycerol',   'Emulsifier',                 'Codex CXG 36-1989'),
    ('472c',    'E472c', 'Citric and fatty acid esters of glycerol',   'Emulsifier',                 'Codex CXG 36-1989'),
    ('472e',    'E472e', 'Diacetyltartaric and fatty acid esters of glycerol', 'Emulsifier',         'Codex CXG 36-1989'),
    ('473',     'E473',  'Sucrose esters of fatty acids',              'Emulsifier',                 'Codex CXG 36-1989'),
    ('475',     'E475',  'Polyglycerol esters of fatty acids',         'Emulsifier',                 'Codex CXG 36-1989'),
    ('476',     'E476',  'Polyglycerol esters of interesterified ricinoleic acid', 'Emulsifier',     'Codex CXG 36-1989'),
    ('477',     'E477',  'Propylene glycol esters of fatty acids',     'Emulsifier',                 'Codex CXG 36-1989'),
    ('481',     'E481',  'Sodium stearoyl lactylate',                  'Emulsifier / stabilizer',    'Codex CXG 36-1989'),
    ('482',     'E482',  'Calcium stearoyl lactylate',                 'Emulsifier / stabilizer',    'Codex CXG 36-1989'),
    ('491',     'E491',  'Sorbitan monostearate',                      'Emulsifier',                 'Codex CXG 36-1989'),
    ('492',     'E492',  'Sorbitan tristearate',                       'Emulsifier',                 'Codex CXG 36-1989'),
    -- Acidity regulators, raising agents, anticaking agents
    ('500',     'E500',  'Sodium carbonates',                          'Acidity regulator / raising agent', 'Codex CXG 36-1989'),
    ('500(i)',  'E500i', 'Sodium carbonate',                           'Acidity regulator / raising agent', 'Codex CXG 36-1989'),
    ('500(ii)', 'E500ii','Sodium hydrogen carbonate',                  'Acidity regulator / raising agent', 'Codex CXG 36-1989'),
    ('501',     'E501',  'Potassium carbonates',                       'Acidity regulator / stabilizer', 'Codex CXG 36-1989'),
    ('501(ii)', 'E501ii','Potassium hydrogen carbonate',               'Acidity regulator / raising agent', 'Codex CXG 36-1989'),
    ('503',     'E503',  'Ammonium carbonates',                        'Acidity regulator / raising agent', 'Codex CXG 36-1989'),
    ('503(i)',  'E503i', 'Ammonium carbonate',                         'Acidity regulator / raising agent', 'Codex CXG 36-1989'),
    ('503(ii)', 'E503ii','Ammonium hydrogen carbonate',                'Acidity regulator / raising agent', 'Codex CXG 36-1989'),
    ('504',     'E504',  'Magnesium carbonates',                       'Acidity regulator / anticaking agent', 'Codex CXG 36-1989'),
    ('507',     'E507',  'Hydrochloric acid',                          'Acidity regulator',          'Codex CXG 36-1989'),
    ('508',     'E508',  'Potassium chloride',                         'Flavour enhancer / stabilizer', 'Codex CXG 36-1989'),
    ('509',     'E509',  'Calcium chloride',                           'Firming agent / stabilizer', 'Codex CXG 36-1989'),
    ('511',     'E511',  'Magnesium chloride',                         'Firming agent / stabilizer', 'Codex CXG 36-1989'),
    ('516',     'E516',  'Calcium sulfate',                            'Firming agent / flour treatment agent', 'Codex CXG 36-1989'),
    ('535',     'E535',  'Sodium ferrocyanide',                        'Anticaking agent',           'Codex CXG 36-1989'),
    ('536',     'E536',  'Potassium ferrocyanide',                     'Anticaking agent',           'Codex CXG 36-1989'),
    ('541',     'E541',  'Sodium aluminium phosphate',                 'Acidity regulator / emulsifier', 'Codex CXG 36-1989'),
    ('551',     'E551',  'Silicon dioxide, amorphous',                 'Anticaking agent',           'Codex CXG 36-1989'),
    ('570',     'E570',  'Fatty acids',                                'Glazing agent / emulsifier', 'Codex CXG 36-1989'),
    ('575',     'E575',  'Glucono delta-lactone',                      'Acidity regulator / raising agent', 'Codex CXG 36-1989'),
    -- Flavour enhancers
    ('620',     'E620',  'Glutamic acid, L(+)-',                       'Flavour enhancer',           'Codex CXG 36-1989'),
    ('621',     'E621',  'Monosodium L-glutamate',                     'Flavour enhancer',           'Codex CXG 36-1989'),
    ('622',     'E622',  'Monopotassium L-glutamate',                  'Flavour enhancer',           'Codex CXG 36-1989'),
    ('626',     'E626',  'Guanylic acid, 5''-',                        'Flavour enhancer',           'Codex CXG 36-1989'),
    ('627',     'E627',  'Disodium 5''-guanylate',                     'Flavour enhancer',           'Codex CXG 36-1989'),
    ('630',     'E630',  'Inosinic acid, 5''-',                        'Flavour enhancer',           'Codex CXG 36-1989'),
    ('631',     'E631',  'Disodium 5''-inosinate',                     'Flavour enhancer',           'Codex CXG 36-1989'),
    ('634',     'E634',  'Calcium 5''-ribonucleotides',                'Flavour enhancer',           'Codex CXG 36-1989'),
    ('635',     'E635',  'Disodium 5''-ribonucleotides',               'Flavour enhancer',           'Codex CXG 36-1989'),
    ('636',     'E636',  'Maltol',                                     'Flavour enhancer',           'Codex CXG 36-1989'),
    ('637',     'E637',  'Ethyl maltol',                               'Flavour enhancer',           'Codex CXG 36-1989'),
    ('640',     'E640',  'Glycine',                                    'Flavour enhancer',           'Codex CXG 36-1989'),
    -- Glazing agents, antifoaming, flour treatment
    ('900a',    'E900a', 'Polydimethylsiloxane',                       'Antifoaming agent / anticaking agent', 'Codex CXG 36-1989'),
    ('901',     'E901',  'Beeswax',                                    'Glazing agent',              'Codex CXG 36-1989'),
    ('903',     'E903',  'Carnauba wax',                               'Glazing agent',              'Codex CXG 36-1989'),
    ('904',     'E904',  'Shellac',                                    'Glazing agent',              'Codex CXG 36-1989'),
    ('920',     'E920',  'L-Cysteine',                                 'Flour treatment agent',      'Codex CXG 36-1989'),
    ('941',     'E941',  'Nitrogen',                                   'Packaging gas',              'Codex CXG 36-1989'),
    -- Sweeteners
    ('950',     'E950',  'Acesulfame potassium',                       'Sweetener',                  'Codex CXG 36-1989'),
    ('951',     'E951',  'Aspartame',                                  'Sweetener',                  'Codex CXG 36-1989'),
    ('952',     'E952',  'Cyclamates',                                 'Sweetener',                  'Codex CXG 36-1989'),
    ('954',     'E954',  'Saccharins',                                 'Sweetener',                  'Codex CXG 36-1989'),
    ('955',     'E955',  'Sucralose',                                  'Sweetener',                  'Codex CXG 36-1989'),
    ('960',     'E960',  'Steviol glycosides',                         'Sweetener',                  'Codex CXG 36-1989'),
    ('961',     'E961',  'Neotame',                                    'Sweetener',                  'Codex CXG 36-1989'),
    ('965',     'E965',  'Maltitol',                                   'Sweetener / humectant',      'Codex CXG 36-1989'),
    ('966',     'E966',  'Lactitol',                                   'Sweetener / emulsifier',     'Codex CXG 36-1989'),
    ('967',     'E967',  'Xylitol',                                    'Sweetener / humectant',      'Codex CXG 36-1989'),
    ('968',     'E968',  'Erythritol',                                 'Sweetener / humectant',      'Codex CXG 36-1989'),
    -- Enzymes, modified starches, carriers
    ('1100',    'E1100', 'Amylases',                                   'Flour treatment agent',      'Codex CXG 36-1989'),
    ('1101(i)', 'E1101i','Protease',                                   'Flour treatment agent',      'Codex CXG 36-1989'),
    ('1101(ii)','E1101ii','Papain',                                    'Flour treatment agent',      'Codex CXG 36-1989'),
    ('1101(iii)','E1101iii','Bromelain',                               'Flour treatment agent',      'Codex CXG 36-1989'),
    ('1200',    'E1200', 'Polydextroses',                              'Bulking agent / stabilizer', 'Codex CXG 36-1989'),
    ('1400',    'E1400', 'Dextrins, roasted starch',                   'Thickener / stabilizer',     'Codex CXG 36-1989'),
    ('1404',    'E1404', 'Oxidized starch',                            'Thickener / emulsifier',     'Codex CXG 36-1989'),
    ('1410',    'E1410', 'Monostarch phosphate',                       'Thickener / stabilizer',     'Codex CXG 36-1989'),
    ('1412',    'E1412', 'Distarch phosphate',                         'Thickener / stabilizer',     'Codex CXG 36-1989'),
    ('1413',    'E1413', 'Phosphated distarch phosphate',              'Thickener / stabilizer',     'Codex CXG 36-1989'),
    ('1414',    'E1414', 'Acetylated distarch phosphate',              'Thickener / emulsifier',     'Codex CXG 36-1989'),
    ('1420',    'E1420', 'Starch acetate',                             'Thickener / stabilizer',     'Codex CXG 36-1989'),
    ('1422',    'E1422', 'Acetylated distarch adipate',                'Thickener / stabilizer',     'Codex CXG 36-1989'),
    ('1440',    'E1440', 'Hydroxypropyl starch',                       'Thickener / emulsifier',     'Codex CXG 36-1989'),
    ('1442',    'E1442', 'Hydroxypropyl distarch phosphate',           'Thickener / stabilizer',     'Codex CXG 36-1989'),
    ('1450',    'E1450', 'Starch sodium octenyl succinate',            'Emulsifier / stabilizer',    'Codex CXG 36-1989'),
    ('1451',    'E1451', 'Acetylated oxidized starch',                 'Thickener / emulsifier',     'Codex CXG 36-1989'),
    ('1505',    'E1505', 'Triethyl citrate',                           'Carrier / sequestrant',      'Codex CXG 36-1989'),
    ('1518',    'E1518', 'Triacetin',                                  'Humectant / carrier',        'Codex CXG 36-1989'),
    ('1520',    'E1520', 'Propylene glycol',                           'Humectant / carrier',        'Codex CXG 36-1989');

COMMENT ON TABLE additive IS
    'INS reference names and Codex functional classes only (CXG 36-1989). Carries '
    'no safety, permission or health information and must never be used to '
    'imply one. A label token that matches no row here is still marked '
    'is_additive on product_ingredient, with additive_code left NULL.';

ALTER TABLE product_ingredient
    ADD COLUMN additive_code text REFERENCES additive(ins_number);

CREATE INDEX ix_product_ingredient_additive ON product_ingredient (additive_code)
    WHERE additive_code IS NOT NULL;


-- -----------------------------------------------------------------------------
-- Category aliases — what people type, mapped to the closed category set
-- -----------------------------------------------------------------------------
CREATE TABLE category_alias (
    alias         text PRIMARY KEY,
    category_code text NOT NULL REFERENCES category(code)
);

CREATE INDEX ix_category_alias_category ON category_alias (category_code);

INSERT INTO category_alias (alias, category_code) VALUES
    ('biscuit', 'BISCUITS'), ('biscuits', 'BISCUITS'), ('cookie', 'BISCUITS'), ('cookies', 'BISCUITS'),
    ('cracker', 'BISCUITS'), ('crackers', 'BISCUITS'), ('rusk', 'BISCUITS'),
    ('cereal', 'BREAKFAST_CEREAL'), ('cereals', 'BREAKFAST_CEREAL'), ('cornflakes', 'BREAKFAST_CEREAL'),
    ('corn flakes', 'BREAKFAST_CEREAL'), ('muesli', 'BREAKFAST_CEREAL'), ('granola', 'BREAKFAST_CEREAL'),
    ('oats', 'BREAKFAST_CEREAL'), ('porridge', 'BREAKFAST_CEREAL'), ('breakfast cereal', 'BREAKFAST_CEREAL'),
    ('namkeen', 'NAMKEEN'), ('bhujia', 'NAMKEEN'), ('mixture', 'NAMKEEN'), ('sev', 'NAMKEEN'),
    ('savoury snacks', 'NAMKEEN'), ('savory snacks', 'NAMKEEN'), ('snacks', 'NAMKEEN'),
    ('drink', 'BEVERAGES'), ('drinks', 'BEVERAGES'), ('beverage', 'BEVERAGES'), ('beverages', 'BEVERAGES'),
    ('juice', 'BEVERAGES'), ('juices', 'BEVERAGES'), ('soda', 'BEVERAGES'), ('cold drink', 'BEVERAGES'),
    ('soft drink', 'BEVERAGES'), ('soft drinks', 'BEVERAGES'), ('energy drink', 'BEVERAGES'),
    ('squash', 'BEVERAGES'), ('tea', 'BEVERAGES'), ('coffee', 'BEVERAGES'), ('water', 'BEVERAGES'),
    ('malt drink', 'MALT_DRINK'), ('malt', 'MALT_DRINK'), ('health drink', 'MALT_DRINK'),
    ('malted drink', 'MALT_DRINK'), ('drink mix', 'MALT_DRINK'),
    ('atta', 'STAPLES'), ('flour', 'STAPLES'), ('maida', 'STAPLES'), ('rice', 'STAPLES'), ('bread', 'STAPLES'),
    ('dal', 'STAPLES'), ('pulses', 'STAPLES'), ('staples', 'STAPLES'), ('grains', 'STAPLES'),
    ('milk', 'DAIRY'), ('curd', 'DAIRY'), ('dahi', 'DAIRY'), ('paneer', 'DAIRY'), ('cheese', 'DAIRY'),
    ('yogurt', 'DAIRY'), ('yoghurt', 'DAIRY'), ('dairy', 'DAIRY'), ('butter', 'DAIRY'), ('lassi', 'DAIRY'),
    ('oil', 'EDIBLE_OIL'), ('oils', 'EDIBLE_OIL'), ('ghee', 'EDIBLE_OIL'), ('cooking oil', 'EDIBLE_OIL'),
    ('edible oil', 'EDIBLE_OIL'), ('sunflower oil', 'EDIBLE_OIL'), ('mustard oil', 'EDIBLE_OIL'),
    ('chocolate', 'CHOCOLATE'), ('chocolates', 'CHOCOLATE'), ('candy', 'CHOCOLATE'), ('candies', 'CHOCOLATE'),
    ('sweets', 'CHOCOLATE'), ('toffee', 'CHOCOLATE'), ('confectionery', 'CHOCOLATE'),
    ('noodles', 'INSTANT_NOODLES'), ('instant noodles', 'INSTANT_NOODLES'), ('pasta', 'INSTANT_NOODLES'),
    ('vermicelli', 'INSTANT_NOODLES'),
    ('ketchup', 'SAUCES_SPREADS'), ('sauce', 'SAUCES_SPREADS'), ('sauces', 'SAUCES_SPREADS'),
    ('jam', 'SAUCES_SPREADS'), ('spread', 'SAUCES_SPREADS'), ('spreads', 'SAUCES_SPREADS'),
    ('chutney', 'SAUCES_SPREADS'), ('mayonnaise', 'SAUCES_SPREADS'), ('mayo', 'SAUCES_SPREADS'),
    ('pickle', 'SAUCES_SPREADS'), ('achar', 'SAUCES_SPREADS'), ('peanut butter', 'SAUCES_SPREADS'),
    ('protein', 'PROTEIN_FOODS'), ('protein bar', 'PROTEIN_FOODS'), ('protein bars', 'PROTEIN_FOODS'),
    ('protein powder', 'PROTEIN_FOODS'), ('whey', 'PROTEIN_FOODS'), ('nuts', 'PROTEIN_FOODS'),
    ('chips', 'CHIPS'), ('crisps', 'CHIPS'), ('wafers', 'CHIPS'), ('nachos', 'CHIPS'), ('potato chips', 'CHIPS');


-- -----------------------------------------------------------------------------
-- Unknown barcodes — scans that hit nothing (public, non-personal)
-- -----------------------------------------------------------------------------
CREATE TABLE unknown_barcode (
    gtin                text PRIMARY KEY,
    first_seen          timestamptz NOT NULL DEFAULT now(),
    last_seen           timestamptz NOT NULL DEFAULT now(),
    hit_count           int NOT NULL DEFAULT 1 CHECK (hit_count > 0),
    status              text NOT NULL DEFAULT 'NEW'
                          CHECK (status IN ('NEW','CONTRIBUTED','RESOLVED','IGNORED')),
    resolved_product_id uuid REFERENCES product(id) ON DELETE SET NULL
);

CREATE INDEX ix_unknown_barcode_status ON unknown_barcode (status, hit_count DESC);

COMMENT ON TABLE unknown_barcode IS
    'A GTIN that was scanned but matched no product. Deliberately carries no '
    'account or member id: which barcodes are missing from the catalogue is '
    'catalogue-improvement data, not personal data.';


-- -----------------------------------------------------------------------------
-- Brand alias seeds — for brands already present in this database.
--
-- INSERT ... SELECT so the migration is a no-op on an empty catalogue; the
-- same alias map lives in catalog.application.BrandAliases and is applied by
-- ProductIngestionService when a brand row is first created, so a fresh
-- import gets the same aliases without a second migration.
-- -----------------------------------------------------------------------------
INSERT INTO brand_alias (brand_id, alias, normalized_alias)
SELECT b.id, a.alias, a.alias
FROM (VALUES
    ('britannia',    'britania'),
    ('britannia',    'brittania'),
    ('amul',         'amool'),
    ('parle',        'parle g'),
    ('parle',        'parle-g'),
    ('haldiram''s',  'haldiram'),
    ('haldiram''s',  'haldirams'),
    ('lay''s',       'lays'),
    ('maggi',        'maggie'),
    ('cadbury',      'cadburys'),
    ('cadbury',      'cadbury''s'),
    ('kellogg''s',   'kellogs'),
    ('kellogg''s',   'kelloggs'),
    ('kellogg''s',   'kellogg'),
    ('nestle',       'nestlé'),
    ('coca-cola',    'coca cola'),
    ('coca-cola',    'coke'),
    ('mother dairy', 'motherdairy'),
    ('hershey''s',   'hersheys'),
    ('mcvitie''s',   'mcvities'),
    ('bagrry''s',    'bagrrys'),
    ('too yumm',     'tooyumm'),
    ('sunfeast',     'sun feast'),
    ('kurkure',      'kur kure'),
    ('paper boat',   'paperboat'),
    ('pepsico',      'pepsi'),
    ('saffola',      'safola'),
    ('bikaji',       'bikaji namkeen'),
    ('yoga bar',     'yogabar')
) AS a(brand_name, alias)
JOIN brand b ON b.normalized_name = a.brand_name
ON CONFLICT (brand_id, normalized_alias) DO NOTHING;


-- -----------------------------------------------------------------------------
-- product_image gallery columns (Increment 1b, D-073)
--
-- purpose: which view of the pack this photo shows. derived_from_image_id:
-- a cleaned/cropped derivative keeps its parent (the original OFF upload) so
-- attribution and share-alike travel with it. license_note: the CC BY-SA 3.0
-- attribution for OFF-sourced photos, per row, like product_source.license_note.
-- -----------------------------------------------------------------------------
-- A replaced original is SUPERSEDED, not REJECTED: it was not wrong, a better
-- photo of the same view arrived. Nothing is deleted (same spirit as
-- product_version). V11 named this constraint implicitly.
ALTER TABLE product_image DROP CONSTRAINT product_image_status_check;
ALTER TABLE product_image
    ADD CONSTRAINT product_image_status_check
        CHECK (status IN ('UNVERIFIED','VERIFIED','REJECTED','SUPERSEDED'));

ALTER TABLE product_image
    ADD COLUMN purpose               text NOT NULL DEFAULT 'FRONT'
                                       CHECK (purpose IN ('FRONT','BACK','PACKAGING','NUTRITION',
                                                          'INGREDIENTS','OTHER')),
    ADD COLUMN sort_order            int NOT NULL DEFAULT 0,
    ADD COLUMN derived_from_image_id uuid REFERENCES product_image(id) ON DELETE SET NULL,
    ADD COLUMN transform             jsonb,
    ADD COLUMN quality_score         numeric(3,2)
                                       CHECK (quality_score IS NULL OR quality_score BETWEEN 0 AND 1),
    ADD COLUMN classification        jsonb,
    ADD COLUMN license_note          text;

-- At most one live original per (product, purpose). Derivatives (cleaned
-- crops) sit outside this rule: they point at their parent instead. The
-- catalogue-image write path (ProductImageService) marks the previous live
-- original of the same purpose SUPERSEDED before inserting a replacement.
CREATE UNIQUE INDEX uq_product_image_purpose
    ON product_image (product_id, purpose)
    WHERE status IN ('UNVERIFIED','VERIFIED') AND derived_from_image_id IS NULL;

CREATE INDEX ix_product_image_derived ON product_image (derived_from_image_id)
    WHERE derived_from_image_id IS NOT NULL;
