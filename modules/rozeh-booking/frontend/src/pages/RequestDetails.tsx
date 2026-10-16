import {useState} from 'react';
import {Alert, Button, Icon, Loading, ModuleBar, SectionTitle, StatusBadge, Summary, TextAreaField} from '@haramyar/design-system';
import {formatDateTime} from '@haramyar/i18n';
import {api, type BookingView} from '../api.ts';
import {useEnv, useLoad} from '../env.tsx';
import {codeLabel, statusLabel, tracking, when} from '../labels.ts';
import type {MessageKey} from '../messages.ts';
import {Timeline} from './Timeline.tsx';

function takeSubmittedFlag(id: string): boolean {
  try {
    if (sessionStorage.getItem('haramyar.rozeh.submitted') !== id) return false;
    sessionStorage.removeItem('haramyar.rozeh.submitted');
    return true;
  } catch {
    return false;
  }
}

/** Host view of one own booking: real status, history and only the actions the policies currently allow. */
export function RequestDetails() {
  const {t, navigate, locale, params, route, errorText} = useEnv();
  const id = params.bookingId ?? route.split('/').pop() ?? '';
  const [justSubmitted] = useState(() => takeSubmittedFlag(id));
  const booking = useLoad(() => api.mineDetails(id), [id]);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState('');
  const [copied, setCopied] = useState(false);

  const act = async (action: (b: BookingView) => Promise<BookingView>) => {
    if (!booking.data) return;
    setBusy(true);
    setFailure(null);
    try {
      booking.setData(await action(booking.data));
      setCancelling(false);
    } catch (error) {
      setFailure(errorText(error, route));
      booking.reload();
    } finally {
      setBusy(false);
    }
  };

  if (booking.loading && !booking.data) return <div className="module-page"><ModuleBar title={t('requestDetails')} onBack={() => navigate('/rozeh/requests')} backLabel={t('back')} /><Loading label={t('loading')} /></div>;
  if (booking.error || !booking.data) return (
    <div className="module-page"><ModuleBar title={t('requestDetails')} onBack={() => navigate('/rozeh/requests')} backLabel={t('back')} />
      <div className="module-body"><Alert kind="error">{errorText(booking.error, route)}</Alert><Button variant="ghost" onClick={booking.reload}>{t('retry')}</Button></div></div>
  );

  const b = booking.data;
  const time = when(locale, b.time.startsAt, b.time.endsAt, b.time.timeZone);
  const code = tracking(b.trackingCode, locale);
  const hostConfirms = b.allowedActions.includes('CONFIRM');
  const explain = b.status === 'ACCEPTED' && !hostConfirms ? 'explain_ACCEPTED_COORDINATOR' : `explain_${b.status}`;

  return (
    <div className={`module-page ${justSubmitted ? 'success-page' : ''}`}>
      <ModuleBar title={t('requestDetails')} onBack={() => navigate('/rozeh/requests')} backLabel={t('back')} />
      <div className="module-body request-detail">
        {justSubmitted ? (
          <div className="success-content" style={{paddingTop: 30}}>
            <div className="success-rings"><span><Icon name="check" size={36} /></span></div>
            <i>{t('submittedLabel')}</i>
            <h1>{t('submittedTitle')}</h1>
            <StatusBadge status={b.status} label={statusLabel(t, b.status)} />
            <p>{t('submittedHint')}</p>
            <div className="tracking">
              <small>{t('requestDetails')}</small>
              <b data-testid="tracking-code" className="ltr">{code}</b>
              <button onClick={() => { void navigator.clipboard?.writeText(b.trackingCode).then(() => setCopied(true)); }}>{copied ? t('copied') : t('copy')}</button>
            </div>
          </div>
        ) : (
          <div className={`request-state ${b.status.toLowerCase()}`}>
            <small>{t('currentStatus')}</small>
            <StatusBadge status={b.status} label={statusLabel(t, b.status)} />
            <h1>{t(`headline_${b.status}` as MessageKey)}</h1>
            <p>{t(explain as MessageKey)}</p>
          </div>
        )}
        <Summary title={t('ceremonyDetails')} rows={[
          [t('date'), time.date], [t('time'), <span className="ltr">{time.time}</span>], [t('category'), codeLabel(t, 'category', b.details.category)],
          [t('audience'), codeLabel(t, 'audience', b.details.audience)], [t('cityArea'), [b.location.city, b.location.area].filter(Boolean).join('، ')],
          [t('address'), b.location.address], ...(b.provider ? [[t('provider'), b.provider.displayName ?? '—'] as [string, string]] : []),
          ['#', <span className="ltr" data-testid="details-tracking-code">{code}</span>]]} />
        <SectionTitle title={t('progress')} />
        <Timeline booking={b} viewer="HOST" />
        {failure && <Alert kind="error">{failure}</Alert>}
        {hostConfirms && <Button onClick={() => act(x => api.confirmMine(x.id, x.version))} disabled={busy} testId="confirm-booking">{t('confirmFinal')}</Button>}
        {b.allowedActions.includes('CANCEL') && !cancelling && <Button variant="ghost" onClick={() => setCancelling(true)} disabled={busy} testId="cancel-booking">{t('cancelRequest')}</Button>}
        {cancelling && (
          <div className="panel">
            <p>{t('confirmCancel')}</p>
            <TextAreaField label={t('cancelReason')} optionalLabel={t('optional')} value={reason} onChange={e => setReason(e.target.value)} maxLength={500} />
            <Button variant="danger" onClick={() => act(x => api.cancelMine(x.id, x.version, reason.trim() || undefined))} disabled={busy} testId="confirm-cancel">{t('yesCancel')}</Button>
            <Button variant="ghost" onClick={() => setCancelling(false)}>{t('keep')}</Button>
          </div>
        )}
        {justSubmitted && <Button variant="ghost" onClick={() => navigate('/')}>{t('backHome')}</Button>}
        <p className="privacy" style={{marginTop: 16}}><Icon name="clock" size={14} /> {formatDateTime(b.updatedAt, locale)}</p>
      </div>
    </div>
  );
}
