import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '@/lib/api';
import { getProductByBarcode } from '@/lib/catalog';
import { t } from '@/lib/i18n';
import { Card } from '@/design-system/Card';
import { ContributeProductForm } from './ContributeProductForm';
import './ScanScreen.css';

type Detector = { detect: (source: ImageBitmapSource) => Promise<Array<{ rawValue?: string }>> };
declare global { interface Window { BarcodeDetector?: new (options?: { formats?: string[] }) => Detector; } }

/** A genuine progressively-enhanced scanner, with a manual lookup fallback. */
export function ScanScreen() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const timerRef = useRef<number | null>(null);
  const [barcode, setBarcode] = useState('');
  const [cameraState, setCameraState] = useState<'idle' | 'active' | 'unavailable'>('idle');
  const [lookupState, setLookupState] = useState<'idle' | 'loading' | 'not-found' | 'error'>('idle');
  // A barcode this app has never seen: offer the hand-entry contribution form,
  // pre-filled with the code that was just looked up. This is a plain local
  // toggle within the scan screen, the same mechanism it already uses to swap
  // between the camera view and the manual-entry fallback — not a new route,
  // since the form only ever makes sense right after a failed lookup here.
  const [contributing, setContributing] = useState(false);
  const isLabelMode = params.get('mode') === 'label';

  function exitContribute() {
    setContributing(false);
    setLookupState('idle');
    setBarcode('');
  }

  function stopCamera() {
    if (timerRef.current !== null) { window.clearInterval(timerRef.current); timerRef.current = null; }
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    setCameraState('idle');
  }

  useEffect(() => () => {
    if (timerRef.current !== null) window.clearInterval(timerRef.current);
    streamRef.current?.getTracks().forEach((track) => track.stop());
  }, []);

  async function lookUp(value: string) {
    const cleaned = value.replace(/\s/g, '');
    if (!cleaned) return;
    setBarcode(cleaned);
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
    if (!window.BarcodeDetector || !navigator.mediaDevices?.getUserMedia) { setCameraState('unavailable'); return; }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false });
      streamRef.current = stream;
      const video = videoRef.current;
      if (!video) { stopCamera(); return; }
      video.srcObject = stream;
      await video.play();
      const detector = new window.BarcodeDetector({ formats: ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128'] });
      setCameraState('active');
      timerRef.current = window.setInterval(() => {
        void detector.detect(video).then((codes) => {
          const value = codes[0]?.rawValue;
          if (value) { stopCamera(); void lookUp(value); }
        }).catch(() => { /* an unavailable video frame is safe to ignore */ });
      }, 350);
    } catch { stopCamera(); setCameraState('unavailable'); }
  }

  if (contributing) {
    return <ContributeProductForm gtin={barcode} onExit={exitContribute} />;
  }

  return (
    <div className="scanpage">
      <div className="scanpage__switch" role="tablist" aria-label={t('nav.scan')}>
        <Link className={!isLabelMode ? 'scanpage__tab scanpage__tab--active' : 'scanpage__tab'} to="/scan" role="tab" aria-selected={!isLabelMode}>{t('scan.heading')}</Link>
        <Link className={isLabelMode ? 'scanpage__tab scanpage__tab--active' : 'scanpage__tab'} to="/scan?mode=label" role="tab" aria-selected={isLabelMode}>{t('capture.heading')}</Link>
      </div>
      {isLabelMode ? <LabelCaptureNotice /> : (
        <Card as="section" padding="lg" elevated className="scanpage__panel" aria-labelledby="scan-title">
          <div className="scanpage__intro"><span className="scanpage__icon" aria-hidden="true">▣</span><div><h1 id="scan-title">{t('scan.heading')}</h1><p>{t('scan.hint')}</p></div></div>
          {cameraState === 'active' ? (
            <div className="scanner-view"><video ref={videoRef} className="scanner-view__video" muted playsInline /><div className="scanner-view__guide" aria-hidden="true" /><button type="button" className="btn btn--secondary scanpage__camera-stop" onClick={stopCamera}>{t('scan.stopCamera')}</button></div>
          ) : (
            <button type="button" className="scanpage__camera" onClick={() => void startCamera()}><span className="scanpage__camera-icon" aria-hidden="true">⌁</span><span>{t('scan.openCamera')}</span></button>
          )}
          {cameraState === 'unavailable' && <p className="scanpage__notice">{t('scan.notSupported')}</p>}
          <div className="scanpage__divider"><span>{t('scan.or')}</span></div>
          <form className="scanpage__manual" onSubmit={(event) => { event.preventDefault(); void lookUp(barcode); }}>
            <label htmlFor="barcode">{t('scan.enterManually')}</label>
            <div className="scanpage__fieldrow"><input id="barcode" inputMode="numeric" autoComplete="off" placeholder={t('scan.barcodePlaceholder')} value={barcode} onChange={(event) => { setBarcode(event.target.value); setLookupState('idle'); }} /><button className="btn btn--primary" type="submit" disabled={!barcode.trim() || lookupState === 'loading'}>{lookupState === 'loading' ? t('loading.default') : t('action.lookUp')}</button></div>
          </form>
          {lookupState === 'not-found' && (
            <div className="scanpage__notice">
              <p>{t('scan.notFound')}</p>
              <button type="button" className="btn btn--primary scanpage__contribute-cta" onClick={() => setContributing(true)}>
                {t('contribute.heading')}
              </button>
            </div>
          )}
          {lookupState === 'error' && <p className="scanpage__notice">{t('error.service_unavailable')}</p>}
          <Link to="/search" className="scanpage__search-link">{t('scan.searchInstead')}</Link>
        </Card>
      )}
    </div>
  );
}

function LabelCaptureNotice() {
  return (
    <Card as="section" padding="lg" elevated className="scanpage__panel scanpage__panel--label" aria-labelledby="label-title">
      <span className="scanpage__icon scanpage__icon--label" aria-hidden="true">◉</span>
      <h1 id="label-title">{t('capture.heading')}</h1>
      <p className="scanpage__label-copy">{t('capture.hint')}</p>
      <p className="scanpage__notice">{t('capture.unavailable')}</p>
      <Link to="/search" className="btn btn--primary">{t('scan.searchInstead')}</Link>
    </Card>
  );
}
