import { useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t, type MessageKey } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import { useMe } from '@/lib/useMe';
import { DASHBOARD_QUERY_KEYS, useFamilyOverview, type OverviewMember } from '@/lib/dashboard';
import { searchProducts, type ProductSummary } from '@/lib/catalog';
import { logMeal, MEAL_TYPES, type MealItemInput, type MealType } from '@/lib/nutritionLog';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { displayProductName, normaliseBrand } from '@/lib/productName';
import { Button } from '@/design-system/Button';
import { ErrorState, Skeleton } from '@/design-system/States';
import { StepIndicator } from '@/design-system/StepIndicator';
import { PageHeader } from '@/design-system/PageHeader';
import { RelationshipChip } from '@/design-system/RelationshipChip';
import './MealLogScreen.css';

/**
 * Log one meal, once, for everyone who ate it (D-039).
 *
 * <p>Three plain steps, in the order a person actually thinks about a meal — what was cooked,
 * who had some, when — rather than one long form with fieldsets in an arbitrary order (plan §18
 * Journey 2.3). Each step validates only itself, and the flow goes forward and back
 * ({@link StepIndicator}), never forward-only.
 *
 * <p>The one thing this screen must never overstate is whose record a meal reaches. It writes
 * immediately for the logger and children they guard; another adult's share is stored but
 * waits for that adult to confirm it (D-041). Which case applies to a given person is asked of
 * the server, not guessed from their member type: every destination sentence below is sourced
 * from the household overview's {@code relationship}/{@code canLogNutrition}/
 * {@code trackingConsentActive} fields, exactly as the plan requires — this is the fix for a
 * live defect (plan §19 G-02) where the previous version of this screen decided who a meal
 * would reach by checking `memberType === 'CHILD'` on the client.
 */

/** The fractions people reach for. Free entry is possible but rarely wanted. */
const PORTION_CHOICES: Array<{ value: number; labelKey: MessageKey }> = [
  { value: 0.25, labelKey: 'meal.portionQuarter' },
  { value: 0.5, labelKey: 'meal.portionHalf' },
  { value: 0.75, labelKey: 'meal.portionThreeQuarter' },
  { value: 1, labelKey: 'meal.portionWhole' },
  { value: 1.5, labelKey: 'meal.portionOneAndHalf' },
];

const MEAL_TYPE_KEYS: Record<MealType, MessageKey> = {
  BREAKFAST: 'meal.type.BREAKFAST',
  LUNCH: 'meal.type.LUNCH',
  DINNER: 'meal.type.DINNER',
  SNACK: 'meal.type.SNACK',
};

type Step = 1 | 2 | 3;
const TOTAL_STEPS = 3;

/** A row being edited. Catalogue items carry the product; custom ones carry values. */
interface DraftItem {
  key: string;
  product: ProductSummary | null;
  customName: string;
  quantityG: string;
  energyKcal: string;
  proteinG: string;
  carbohydrateG: string;
  fatG: string;
  fibreG: string;
  sugarG: string;
  sodiumMg: string;
}

function emptyItem(): DraftItem {
  return {
    key: Math.random().toString(36).slice(2),
    product: null,
    customName: '',
    quantityG: '',
    energyKcal: '',
    proteinG: '',
    carbohydrateG: '',
    fatG: '',
    fibreG: '',
    sugarG: '',
    sodiumMg: '',
  };
}

/** The meal type a person most likely means, from the time of day. A default, never a rule. */
function guessMealType(): MealType {
  const hour = new Date().getHours();
  if (hour < 11) return 'BREAKFAST';
  if (hour < 16) return 'LUNCH';
  if (hour < 19) return 'SNACK';
  return 'DINNER';
}

/** "yyyy-MM-ddTHH:mm" in local time, for a datetime-local input. */
function nowLocalInput(): string {
  const d = new Date();
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 16);
}

export function MealLogScreen() {
  useDocumentTitleKey('meal.heading');
  const me = useMe();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [step, setStep] = useState<Step>(1);
  const [mealType, setMealType] = useState<MealType>(guessMealType);
  const [name, setName] = useState('');
  const [consumedAt, setConsumedAt] = useState(nowLocalInput);
  const [items, setItems] = useState<DraftItem[]>(() => [emptyItem()]);
  const [portions, setPortions] = useState<Record<string, number>>({});
  const [itemsError, setItemsError] = useState<string | null>(null);
  const [portionsError, setPortionsError] = useState<string | null>(null);

  // The "who" step's destination sentences are sourced from here, never from a raw member
  // list (plan §18 Journey 2.3 / G-02) — this is the same overview Family hub reads.
  const overview = useFamilyOverview(me.householdId);

  // Default: the logger ate a whole portion. The most common case is logging your own meal.
  const effectivePortions = useMemo(() => {
    if (Object.keys(portions).length > 0 || !me.memberId) return portions;
    return { [me.memberId]: 1 };
  }, [portions, me.memberId]);

  const mutation = useMutation({
    mutationFn: () =>
      logMeal({
        householdId: me.householdId as string,
        mealType,
        ...(name.trim() ? { name: name.trim() } : {}),
        consumedAt: new Date(consumedAt).toISOString(),
        items: items.map(toItemInput),
        portions: Object.entries(effectivePortions).map(([householdMemberId, portionFraction]) => ({
          householdMemberId,
          portionFraction,
        })),
      }),
    onSuccess: (meal) => {
      // Every member's day totals and insights may have changed.
      void queryClient.invalidateQueries({ queryKey: ['wellness'] });
      void queryClient.invalidateQueries({ queryKey: ['insights'] });
      void queryClient.invalidateQueries({ queryKey: ['meals'] });
      void queryClient.invalidateQueries({ queryKey: ['goals'] });
      void queryClient.invalidateQueries({ queryKey: DASHBOARD_QUERY_KEYS.all });
      navigate(`/meals/${meal.id}`, { state: { justLogged: true } });
    },
  });

  function updateItem(key: string, patch: Partial<DraftItem>) {
    // A message about a problem the user has just fixed is worse than no message.
    setItemsError(null);
    setItems((prev) => prev.map((it) => (it.key === key ? { ...it, ...patch } : it)));
  }

  function togglePortion(memberId: string) {
    setPortionsError(null);
    setPortions((prev) => {
      const base = Object.keys(prev).length > 0 || !me.memberId ? prev : { [me.memberId]: 1 };
      const next = { ...base };
      if (next[memberId] !== undefined) delete next[memberId];
      else next[memberId] = 1;
      return next;
    });
  }

  function setPortion(memberId: string, value: number) {
    setPortions((prev) => {
      const base = Object.keys(prev).length > 0 || !me.memberId ? prev : { [me.memberId]: 1 };
      return { ...base, [memberId]: value };
    });
  }

  function goToWho() {
    const problem = validateItems(items);
    setItemsError(problem);
    if (problem) return;
    setStep(2);
  }

  function goToWhen() {
    const problem = validatePortions(effectivePortions);
    setPortionsError(problem);
    if (problem) return;
    setStep(3);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  if (me.isPending) {
    return (
      <div className="page mealpage layout-form">
        <PageHeader title={t('meal.heading')} />
        <Skeleton lines={4} label={t('loading.default')} />
      </div>
    );
  }

  if (me.householdId === null || me.memberId === null) {
    return (
      <div className="page mealpage layout-form">
        <PageHeader title={t('meal.heading')} />
        <p className="mealpage__lede">{t('today.needsHousehold')}</p>
        <Link to="/family" className="btn btn--primary btn--md">{t('household.create')}</Link>
      </div>
    );
  }

  const saveError = mutation.error instanceof ApiError ? mutation.error.message : null;

  return (
    <div className="page mealpage layout-form">
      <PageHeader title={t('meal.heading')} lede={t('meal.lede')} backTo="/today" backLabel={t('nav.today')} />
      <StepIndicator current={step} total={TOTAL_STEPS} />

      <form className="mealform" onSubmit={step === 3 ? submit : (e) => e.preventDefault()} noValidate>
        {step === 1 && (
          <fieldset className="mealform__group">
            <legend className="mealform__legend">{t('meal.whatHeading')}</legend>
            <p className="mealform__hint">{t('meal.whatHint')}</p>

            <ol className="mealform__items" role="list">
              {items.map((item, index) => (
                <ItemEditor
                  key={item.key}
                  index={index}
                  item={item}
                  canRemove={items.length > 1}
                  onChange={(patch) => updateItem(item.key, patch)}
                  onRemove={() => setItems((prev) => prev.filter((it) => it.key !== item.key))}
                />
              ))}
            </ol>

            <Button type="button" variant="secondary" size="sm" onClick={() => setItems((prev) => [...prev, emptyItem()])}>
              {t('meal.addItem')}
            </Button>

            {itemsError && <p className="form-error" role="alert">{itemsError}</p>}

            <div className="mealform__actions">
              <Button type="button" onClick={goToWho}>{t('action.continue')}</Button>
              <Link to="/today" className="btn btn--ghost btn--md">{t('action.cancel')}</Link>
            </div>
          </fieldset>
        )}

        {step === 2 && (
          <fieldset className="mealform__group">
            <legend className="mealform__legend">{t('meal.whoHeading')}</legend>
            <p className="mealform__hint">{t('meal.whoHint')}</p>

            {overview.isPending && <Skeleton lines={2} label={t('loading.default')} />}
            {overview.isError && <ErrorState error={overview.error} onRetry={() => void overview.refetch()} />}
            {overview.isSuccess && (
              <ul className="mealform__people" role="list">
                {overview.data.members.map((member) => (
                  <PortionRow
                    key={member.memberId}
                    member={member}
                    selected={effectivePortions[member.memberId] !== undefined}
                    fraction={effectivePortions[member.memberId] ?? 1}
                    onToggle={() => togglePortion(member.memberId)}
                    onFraction={(value) => setPortion(member.memberId, value)}
                  />
                ))}
              </ul>
            )}

            {portionsError && <p className="form-error" role="alert">{portionsError}</p>}

            <div className="mealform__actions">
              <Button type="button" onClick={goToWhen}>{t('action.continue')}</Button>
              <Button type="button" variant="ghost" onClick={() => setStep(1)}>{t('action.back')}</Button>
            </div>
          </fieldset>
        )}

        {step === 3 && (
          <fieldset className="mealform__group">
            <legend className="mealform__legend">{t('meal.whenHeading')}</legend>
            <div className="mealform__row">
              <label className="field">
                <span className="field__label">{t('meal.typeLabel')}</span>
                <select value={mealType} onChange={(e) => setMealType(e.target.value as MealType)}>
                  {MEAL_TYPES.map((type) => (
                    <option key={type} value={type}>{t(MEAL_TYPE_KEYS[type])}</option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span className="field__label">{t('meal.timeLabel')}</span>
                <input
                  type="datetime-local"
                  value={consumedAt}
                  max={nowLocalInput()}
                  onChange={(e) => setConsumedAt(e.target.value)}
                  required
                />
              </label>
            </div>
            <label className="field">
              <span className="field__label">
                {t('meal.nameLabel')} <span className="field__optional">{t('meal.optional')}</span>
              </span>
              <input
                type="text"
                maxLength={120}
                placeholder={t('meal.namePlaceholder')}
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </label>

            {saveError && <p className="form-error" role="alert">{saveError}</p>}

            <div className="mealform__actions">
              <Button type="submit" loading={mutation.isPending}>{t('meal.save')}</Button>
              <Button type="button" variant="ghost" onClick={() => setStep(2)} disabled={mutation.isPending}>
                {t('action.back')}
              </Button>
            </div>
          </fieldset>
        )}
      </form>
    </div>
  );
}

/* ------------------------------------------------------------------- item row */

function ItemEditor({
  index,
  item,
  canRemove,
  onChange,
  onRemove,
}: {
  index: number;
  item: DraftItem;
  canRemove: boolean;
  onChange: (patch: Partial<DraftItem>) => void;
  onRemove: () => void;
}) {
  const [query, setQuery] = useState('');
  const [mode, setMode] = useState<'catalogue' | 'custom'>(item.product ? 'catalogue' : 'custom');

  const results = useQuery({
    queryKey: ['search', 'meal-item', query],
    queryFn: () => searchProducts({ q: query, size: 6 }),
    enabled: mode === 'catalogue' && query.trim().length >= 2 && item.product === null,
  });

  const label = t('meal.itemLabel', { n: index + 1 });

  return (
    <li className="mealitem" aria-label={label}>
      <div className="mealitem__head">
        <span className="mealitem__title">{label}</span>
        <div className="mealitem__mode" role="group" aria-label={t('meal.itemSource')}>
          <button
            type="button"
            className={mode === 'custom' ? 'chip chip--active' : 'chip'}
            aria-pressed={mode === 'custom'}
            onClick={() => { setMode('custom'); onChange({ product: null }); }}
          >
            {t('meal.sourceCustom')}
          </button>
          <button
            type="button"
            className={mode === 'catalogue' ? 'chip chip--active' : 'chip'}
            aria-pressed={mode === 'catalogue'}
            onClick={() => setMode('catalogue')}
          >
            {t('meal.sourceCatalogue')}
          </button>
        </div>
        {canRemove && (
          <button type="button" className="mealitem__remove" onClick={onRemove}>
            {t('meal.removeItem')}
          </button>
        )}
      </div>

      {mode === 'catalogue' ? (
        item.product ? (
          <div className="mealitem__picked">
            <span>
              <strong>{normaliseBrand(item.product.brandName)}</strong>{' '}
              {displayProductName(item.product).text}
            </span>
            <button type="button" className="mealitem__change" onClick={() => onChange({ product: null })}>
              {t('meal.changeProduct')}
            </button>
            <p className="mealform__hint">{t('meal.catalogueHint')}</p>
          </div>
        ) : (
          <div className="mealitem__search">
            <label className="field">
              <span className="field__label">{t('meal.searchLabel')}</span>
              <input
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder={t('search.placeholder')}
                autoComplete="off"
              />
            </label>
            {results.isSuccess && results.data.items.length > 0 && (
              <ul className="mealitem__results" role="list">
                {results.data.items.map((p) => (
                  <li key={p.id}>
                    <button type="button" className="mealitem__result" onClick={() => { onChange({ product: p }); setQuery(''); }}>
                      <strong>{normaliseBrand(p.brandName)}</strong> {displayProductName(p).text}
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {results.isSuccess && results.data.items.length === 0 && (
              <p className="mealform__hint">{t('meal.noCatalogueMatch')}</p>
            )}
          </div>
        )
      ) : (
        <label className="field">
          <span className="field__label">{t('meal.foodName')}</span>
          <input
            type="text"
            maxLength={120}
            value={item.customName}
            placeholder={t('meal.foodNamePlaceholder')}
            onChange={(e) => onChange({ customName: e.target.value })}
          />
        </label>
      )}

      <label className="field mealitem__qty">
        <span className="field__label">{t('meal.quantityLabel')}</span>
        <input
          type="number"
          inputMode="decimal"
          min="1"
          step="any"
          value={item.quantityG}
          onChange={(e) => onChange({ quantityG: e.target.value })}
        />
      </label>

      {mode === 'custom' && (
        <details className="mealitem__nutrition">
          <summary>{t('meal.nutritionOptional')}</summary>
          <p className="mealform__hint">{t('meal.nutritionHint')}</p>
          <div className="mealitem__grid">
            {(
              [
                ['energyKcal', 'today.energy', 'kcal'],
                ['proteinG', 'today.protein', 'g'],
                ['carbohydrateG', 'meal.carbs', 'g'],
                ['fatG', 'meal.fat', 'g'],
                ['fibreG', 'today.fibre', 'g'],
                ['sugarG', 'meal.sugar', 'g'],
                ['sodiumMg', 'today.sodium', 'mg'],
              ] as Array<[keyof DraftItem, MessageKey, string]>
            ).map(([field, labelKey, unit]) => (
              <label key={field} className="field">
                <span className="field__label">{t(labelKey)} ({unit})</span>
                <input
                  type="number"
                  inputMode="decimal"
                  min="0"
                  step="any"
                  value={item[field] as string}
                  onChange={(e) => onChange({ [field]: e.target.value } as Partial<DraftItem>)}
                />
              </label>
            ))}
          </div>
        </details>
      )}
    </li>
  );
}

/* ------------------------------------------------------------------ portion row */

/**
 * One destination sentence, decided from the same three overview fields the plan names —
 * never from `memberType`/age band. `canLogNutrition` is the primary fact (it already encodes
 * D-041's self-and-guarded-child-only rule, so this does not re-derive that from `relationship`
 * itself); `trackingConsentActive` is checked next because a write can still be pointless when
 * consent is known to be off; `isYou` only changes the wording, never the outcome.
 */
function destinationSentence(member: OverviewMember): { key: MessageKey; params: Record<string, string> } {
  if (!member.canLogNutrition) {
    return { key: 'meal.awaitsConfirmation', params: { name: member.displayLabel } };
  }
  if (member.trackingConsentActive === false) {
    return member.isYou
      ? { key: 'meal.destinationConsentOffSelf', params: {} }
      : { key: 'meal.destinationConsentOffChild', params: { name: member.displayLabel } };
  }
  return member.isYou
    ? { key: 'meal.destinationYou', params: {} }
    : { key: 'meal.destinationChild', params: { name: member.displayLabel } };
}

function PortionRow({
  member,
  selected,
  fraction,
  onToggle,
  onFraction,
}: {
  member: OverviewMember;
  selected: boolean;
  fraction: number;
  onToggle: () => void;
  onFraction: (value: number) => void;
}) {
  const checkboxId = `portion-${member.memberId}`;
  const destination = selected ? destinationSentence(member) : null;

  return (
    <li className={selected ? 'portion portion--selected' : 'portion'}>
      <div className="portion__who">
        <input id={checkboxId} type="checkbox" checked={selected} onChange={onToggle} />
        <label htmlFor={checkboxId} className="portion__name">
          {member.displayLabel}
          <RelationshipChip relationship={member.relationship} />
        </label>
      </div>

      {selected && (
        <label className="field portion__amount">
          <span className="sr-only">{t('meal.portionFor', { name: member.displayLabel })}</span>
          <select value={fraction} onChange={(e) => onFraction(Number(e.target.value))}>
            {PORTION_CHOICES.map((choice) => (
              <option key={choice.value} value={choice.value}>{t(choice.labelKey)}</option>
            ))}
          </select>
        </label>
      )}

      {destination && (
        <p className="portion__note">{t(destination.key, destination.params)}</p>
      )}
    </li>
  );
}

/* ------------------------------------------------------------------- helpers */

function toItemInput(item: DraftItem): MealItemInput {
  const quantityG = Number(item.quantityG);
  if (item.product) {
    return { productId: item.product.id, quantityG };
  }
  const per100: Record<string, number> = {};
  for (const field of ['energyKcal', 'proteinG', 'carbohydrateG', 'fatG', 'fibreG', 'sugarG', 'sodiumMg'] as const) {
    const raw = item[field].trim();
    // Only what was actually entered. An empty box is "not known", never zero.
    if (raw !== '') per100[field] = Number(raw);
  }
  return { customName: item.customName.trim(), quantityG, nutritionPer100: per100 };
}

/** Client-side checks that give a specific message; the server re-checks everything. */
function validateItems(items: DraftItem[]): string | null {
  if (items.length === 0) return t('error.meal_has_no_items');
  for (const item of items) {
    if (!item.product && item.customName.trim() === '') return t('meal.validationName');
    const qty = Number(item.quantityG);
    if (!Number.isFinite(qty) || qty <= 0) return t('meal.validationQuantity');
  }
  return null;
}

function validatePortions(portions: Record<string, number>): string | null {
  if (Object.keys(portions).length === 0) return t('error.meal_has_no_portions');
  return null;
}
