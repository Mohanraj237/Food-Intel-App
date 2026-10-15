import common from './common.json';
import search from './search.json';
import product from './product.json';
import compare from './compare.json';
import scan from './scan.json';
import ask from './ask.json';
import ops from './ops.json';
import family from './family.json';
import home from './home.json';

/**
 * The en-IN bundle, split by feature area (D-070) so parallel work on different
 * screens does not collide on one 1300-line file. `t()` and the vocabulary test
 * only ever see the merged, flat result below — which physical file a key lives
 * in has no runtime meaning, only an editing-collision one. New keys for a
 * feature go in that feature's file; anything cross-cutting (verdict/fit/score/
 * allergen vocab, nav, onboarding, settings, wellness screens, etc.) stays in
 * `common.json`, exactly where it already was before the split.
 *
 * Every key across the eight files is disjoint by construction (see the split
 * script's own total-count check at split time); a duplicate key here would be
 * a silent bug, so keep additions to their one home file.
 */
const enIN = {
  ...common,
  ...search,
  ...product,
  ...compare,
  ...scan,
  ...ask,
  ...ops,
  ...family,
  ...home,
} as const;

export default enIN;
