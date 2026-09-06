import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { t } from '@/lib/i18n';
import { ApiError } from '@/lib/api';
import { approveContribution, getContributionDetail, rejectContribution } from '@/lib/ops';
import { Button } from '@/design-system/Button';
import { ErrorState, Skeleton } from '@/design-system/States';
import './OpsScreens.css';

/**
 * Review one contribution: full submitted detail, approve or reject. Approval creates a
 * real catalogue product in the same backend transaction that marks the contribution
 * reviewed (`ContributionService.approve`) — there is no intermediate "draft" state on this
 * screen to get out of sync with the server.
 */
export function OpsReviewScreen() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');

  const detail = useQuery({
    queryKey: ['ops', 'contribution', id],
    queryFn: () => getContributionDetail(id),
    enabled: id !== '',
  });

  const approve = useMutation({
    mutationFn: () => approveContribution(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['ops', 'queue'] });
    },
  });

  const reject = useMutation({
    mutationFn: () => rejectContribution(id, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['ops', 'queue'] });
    },
  });

  if (detail.isPending) {
    return (
      <div className="page ops">
        <Skeleton lines={6} label={t('loading.default')} />
      </div>
    );
  }

  if (detail.isError) {
    return (
      <div className="page ops">
        <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />
      </div>
    );
  }

  const c = detail.data;
  const approveError = approve.error instanceof ApiError ? approve.error.message : null;
  const rejectError = reject.error instanceof ApiError ? reject.error.message : null;

  return (
    <div className="page ops">
      <Link to="/ops" className="ops__back">{t('action.back')}</Link>
      <h1>{c.brandName ? `${c.brandName} — ${c.productName}` : c.productName}</h1>
      <p className="ops__status">{t(`ops.status.${c.status}` as Parameters<typeof t>[0])}</p>

      <dl className="ops__detail">
        <dt>{t('contribute.gtinLabel')}</dt>
        <dd>{c.gtin ?? t('profile.notSpecified')}</dd>
        <dt>{t('contribute.categoryLabel')}</dt>
        <dd>{c.categoryCode ?? t('profile.notSpecified')}</dd>
        <dt>{t('contribute.packSizeLabel')}</dt>
        <dd>{c.packSizeValue != null ? `${c.packSizeValue} ${c.packSizeUnit ?? ''}` : t('profile.notSpecified')}</dd>
        <dt>{t('contribute.servingDeclaredLabel')}</dt>
        <dd>{c.servingDeclaredText ?? t('profile.notSpecified')}</dd>
      </dl>

      <h2 className="settings__h2">{t('contribute.nutrientsHeading')}</h2>
      {c.nutrients.length === 0 ? (
        <p className="ops__empty">{t('product.notDeclared')}</p>
      ) : (
        <table className="nutri">
          <tbody>
            {c.nutrients.map((n) => (
              <tr key={n.nutrientCode}>
                <th scope="row">{n.nutrientCode}</th>
                <td>{n.amountPer100 ?? t('product.notDeclared')}</td>
                <td>{n.amountPerServing ?? ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2 className="settings__h2">{t('contribute.ingredientsLabel')}</h2>
      <p>{c.ingredientsRawText ?? t('product.notDeclared')}</p>

      <h2 className="settings__h2">{t('contribute.allergensLabel')}</h2>
      <p>
        {c.allergenCodes.length === 0
          ? t('product.notDeclared')
          : c.allergenCodes.map((code) => t(`allergenCode.${code}` as Parameters<typeof t>[0])).join(', ')}
      </p>

      {c.status === 'PENDING' && (
        <div className="ops__actions">
          {approveError && <p className="form-error" role="alert">{approveError}</p>}
          {!rejecting ? (
            <>
              <Button loading={approve.isPending} onClick={() => approve.mutate()}>
                {t('ops.approve')}
              </Button>
              <Button variant="destructive" onClick={() => setRejecting(true)}>
                {t('ops.reject')}
              </Button>
            </>
          ) : (
            <div className="ops__reject-form">
              <label className="field">
                <span className="field__label">{t('ops.rejectReasonLabel')}</span>
                <textarea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
              </label>
              {rejectError && <p className="form-error" role="alert">{rejectError}</p>}
              <div className="settings__actions">
                <Button
                  variant="destructive"
                  disabled={!reason.trim()}
                  loading={reject.isPending}
                  onClick={() => reject.mutate()}
                >
                  {t('ops.rejectConfirm')}
                </Button>
                <Button variant="ghost" onClick={() => setRejecting(false)}>
                  {t('action.done')}
                </Button>
              </div>
            </div>
          )}
        </div>
      )}

      {approve.isSuccess && (
        <p className="profile__saved" role="status">
          {t('ops.approveSuccess')}{' '}
          <Link to={`/product/${approve.data.productId}`}>{t('compare.viewProduct')}</Link>
        </p>
      )}
      {reject.isSuccess && (
        <p className="profile__saved" role="status">
          {t('ops.rejectSuccess')} <button type="button" className="ops__back" onClick={() => navigate('/ops')}>{t('action.back')}</button>
        </p>
      )}
    </div>
  );
}
