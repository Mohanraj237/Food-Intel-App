import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '@/lib/api';
import { getProductByBarcode } from '@/lib/catalog';
import { t } from '@/lib/i18n';
import { useDocumentTitleKey } from '@/lib/useDocumentTitle';
import { Icon } from '@/design-system/Icon';
import { EmptyState } from '@/design-system/States';
import { ContributeProductForm } from './ContributeProductForm';
import './ScanScreen.css';

type Detector = { detect: (source: ImageBitmapSource) => Promise<Array<{ rawValue?: string }>> };
declare global {
  interface Window {
    BarcodeDetector?: new (options?: { formats?: string[] }) => Detector;
  }
}

/**
 * Scan: the camera is the screen. A progressively-enhanced scanner (native `BarcodeDetector`
 * where it exists) with a manual barcode fallback that works everywhere.
 */
export function ScanScreen() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const timerRef = useRef<number | null>(null);
  const [barcode, setBarcode] = useState('');
  const [cameraState, setCameraState] = useState<'idle' | 'active' | 'unavailable'>('idle');
  const [lookupState, setLookupState] = useState<'idle' | 'loading' | 'invalid' | 'not-found' | 'error'>('idle');
  const [manualOpen, setManualOpen] = useState(false);
  const [contributing, setContributing] = useState(false);
  const isLabelMode = params.get('mode') === 'label';
  useDocumentTitleKey(isLabelMode ? 'capture.heading' : 'scan.heading');

  function exitContribute() {
    setContributing(false);
    setLookupState('idle');
    setBarcode('');
  }

  function stopCamera() {
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    setCameraState('idle');
  }

  useEffect(
    () => () => {
      if (timerRef.current !== null) window.clearInterval(timerRef.current);
      streamRef.current?.getTracks().forEach((track) => track.stop());
    },
    [],
  );

  async function lookUp(value: string) {
    const cleaned = value.replace(/[\s-]/g, '');
    if (!cleaned) return;
    setBarcode(cleaned);

    if (!/^\d{8,14}$/.test(cleaned)) {
      setLookupState('invalid');
      return;
    }

    setLookupState('loading');
    try {
      const product = await getProductByBarcode(cleaned);
      stopCamera();
      navigate(`/product/${product.id}`);
    } catch (error) {
      setLookupState(error instanceof ApiError && error.status === 404 ? 'not-found' : 'error');
    }
  }

  async function startCamera() {
    if (!window.BarcodeDetector || !navigator.mediaDevices?.getUserMedia) {
      setCameraState('unavailable');
      setManualOpen(true);
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      });
      streamRef.current = stream;
      const video = videoRef.current;
      if (!video) {
        stopCamera();
        return;
      }
      video.srcObject = stream;
      await video.play();
      const detector = new window.BarcodeDetector({ formats: ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128'] });
      setCameraState('active');
      timerRef.current = window.setInterval(() => {
        void detector
          .detect(video)
          .then((codes) => {
            const value = codes[0]?.rawValue;
            if (value) {
              stopCamera();
              void lookUp(value);
            }
          })
          .catch(() => {
            /* an unavailable video frame is safe to ignore */
          });
      }, 350);
    } catch {
      stopCamera();
      setCameraState('unavailable');
      setManualOpen(true);
    }
  }

  if (contributing) {
    return <ContributeProductForm gtin={barcode} onExit={exitContribute} />;
  }

  return (
    <div className="scanpage">
      <nav className="segmented scanpage__switch" aria-label={t('nav.scan')}>
        <Link
          className={!isLabelMode ? 'segmented__item segmented__item--active' : 'segmented__item'}
          to="/scan"
          aria-current={!isLabelMode ? 'page' : undefined}
        >
          {t('scan.heading')}
        </Link>
        <Link
          className={isLabelMode ? 'segmented__item segmented__item--active' : 'segmented__item'}
          to="/scan?mode=label"
          aria-current={isLabelMode ? 'page' : undefined}
        >
          {t('capture.heading')}
        </Link>
      </nav>

      {isLabelMode ? (
        <LabelCaptureNotice />
      ) : (
        <div className="scanpage__main">
          <h1 className="scanpage__title">{t('scan.heading')}</h1>
          <p className="scanpage__hint">{t('scan.hint')}</p>

          <div className={cameraState === 'active' ? 'viewfinder viewfinder--live' : 'viewfinder'}>
            <video ref={videoRef} className="viewfinder__video" muted playsInline />
            <div className="viewfinder__frame" aria-hidden="true">
              <span className="viewfinder__corner viewfinder__corner--tl" />
              <span className="viewfinder__corner viewfinder__corner--tr" />
              <span className="viewfinder__corner viewfinder__corner--bl" />
              <span className="viewfinder__corner viewfinder__corner--br" />
              {cameraState === 'active' && <span className="viewfinder__line" />}
            </div>
            {cameraState !== 'active' && (
              <button type="button" className="viewfinder__start" onClick={() => void startCamera()}>
                <span className="viewfinder__start-icon">
                  <Icon name="camera" size={28} />
                </span>
                <span>{t('scan.openCamera')}</span>
              </button>
            )}
            {cameraState === 'active' && (
              <button type="button" className="btn btn--secondary btn--sm viewfinder__stop" onClick={stopCamera}>
                {t('scan.stopCamera')}
              </button>
            )}
          </div>

          {cameraState === 'unavailable' && <p className="note">{t('scan.notSupported')}</p>}

          <div role="status" aria-live="polite" className="scanpage__lookup-status">
            {lookupState === 'invalid' && <p className="note">{t('scan.barcodeInvalid')}</p>}
            {lookupState === 'not-found' && (
              <EmptyState
                icon="search"
                title={t('scan.notFound')}
                body={t('contribute.explainerShort')}
                action={
                  <button type="button" className="btn btn--primary btn--sm" onClick={() => setContributing(true)}>
                    {t('contribute.heading')}
                  </button>
                }
              />
            )}
            {lookupState === 'error' && <p className="form-error">{t('error.service_unavailable')}</p>}
          </div>

          <div className="scanpage__secondary">
            {manualOpen ? (
              <form
                className="scanpage__manual"
                onSubmit={(event) => {
                  event.preventDefault();
                  void lookUp(barcode);
                }}
              >
                <label htmlFor="barcode" className="field__label">
                  {t('scan.barcodeLabel')}
                </label>
                <div className="scanpage__fieldrow">
                  <input
                    id="barcode"
                    inputMode="numeric"
                    autoComplete="off"
                    placeholder={t('scan.barcodePlaceholder')}
                    value={barcode}
                    onChange={(event) => {
                      setBarcode(event.target.value);
                      setLookupState('idle');
                    }}
                  />
                  <button className="btn btn--primary" type="submit" disabled={!barcode.trim() || lookupState === 'loading'}>
                    {lookupState === 'loading' ? t('loading.default') : t('action.lookUp')}
                  </button>
                </div>
              </form>
            ) : (
              <button type="button" className="btn btn--ghost btn--sm" onClick={() => setManualOpen(true)}>
                <Icon name="edit" size={16} />
                {t('scan.enterManually')}
              </button>
            )}
            <span className="scanpage__or">{t('scan.or')}</span>
            <Link to="/search" className="btn btn--ghost btn--sm">
              <Icon name="search" size={16} />
              {t('scan.searchInstead')}
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}

function LabelCaptureNotice() {
  return (
    <div className="scanpage__main">
      <h1 className="scanpage__title">{t('capture.heading')}</h1>
      <p className="scanpage__hint">{t('capture.hint')}</p>
      <EmptyState
        icon="camera"
        title={t('capture.comingSoonTitle')}
        body={t('capture.unavailable')}
        action={
          <div className="inline-actions">
            <Link to="/scan" className="btn btn--primary btn--sm">
              {t('scan.heading')}
            </Link>
            <Link to="/search" className="btn btn--ghost btn--sm">
              {t('scan.searchInstead')}
            </Link>
          </div>
        }
      />
    </div>
  );
}
