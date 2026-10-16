import {useState} from 'react';
import {Alert, Button, Loading, SectionTitle, SelectField, StatusBadge, TextAreaField} from '@haramyar/design-system';
import {formatDateTime} from '@haramyar/i18n';
import {api, type BookingView} from '../api.ts';
import {useEnv, useLoad} from '../env.tsx';
import {codeLabel, statusLabel, tracking, when} from '../labels.ts';
import {CoordinationLayout} from './CoordinationLayout.tsx';
import {Timeline} from './Timeline.tsx';

/**
 * One booking from the coordinator's perspective. Buttons appear only for transitions the domain policies allow the
 * coordinator capacity right now (allowedActions); the backend re-checks every command.
 */
export function CoordinationDetails() {
  const {t, navigate, locale, params, route, errorText} = useEnv();
  const id = params.bookingId ?? route.split('/').pop() ?? '';
  const booking = useLoad(() => api.coordinationDetails(id), [id]);
  const providers = useLoad(() => api.providers(false), []);
  const [providerId, setProviderId] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const schedule = useLoad(async () => {
    const provider = booking.data?.provider?.id;
    if (!provider) return null;
    const from = new Date();
    const to = new Date(from.getTime() + 14 * 86400000);
    return api.availability(provider, from.toISOString(), to.toISOString());
  }, [booking.data?.provider?.id]);

  const act = async (run: (b: BookingView) => Promise<BookingView>) => {
    if (!booking.data) return;
    setBusy(true);
    setFailure(null);
    try {
      booking.setData(await run(booking.data));
      setNote('');
    } catch (error) {
      setFailure(errorText(error, route));
      booking.reload();
    } finally {
      setBusy(false);
    }
  };

  if (booking.loading && !booking.data) return <CoordinationLayout active="overview"><Loading label={t('loading')} /></CoordinationLayout>;
  if (!booking.data) return <CoordinationLayout active="overview"><Alert kind="error">{errorText(booking.error, route)}</Alert><Button variant="ghost" onClick={booking.reload}>{t('retry')}</Button></CoordinationLayout>;

  const b = booking.data;
  const time = when(locale, b.time.startsAt, b.time.endsAt, b.time.timeZone);
  const allowed = new Set(b.allowedActions);
  const reasonRequired = allowed.has('REJECT') || allowed.has('CANCEL');
  return (
    <CoordinationLayout active="overview">
      <div className="provider-head">
        <div><small>{t('requestDetails')} · <span className="ltr">{tracking(b.trackingCode, locale)}</span></small><h1>{b.host.name}</h1></div>
        <StatusBadge status={b.status} label={statusLabel(t, b.status)} />
      </div>
      <div className="detail-grid">
        <div className="panel">
          <h2>{t('ceremonyDetails')}</h2>
          <dl className="kv">
            <dt>{t('date')}</dt><dd>{time.date}</dd>
            <dt>{t('time')}</dt><dd className="ltr">{time.time}</dd>
            <dt>{t('category')}</dt><dd>{codeLabel(t, 'category', b.details.category)}</dd>
            <dt>{t('audience')}</dt><dd>{codeLabel(t, 'audience', b.details.audience)}</dd>
            <dt>{t('capacity')}</dt><dd>{b.details.approximateCapacity ?? '—'}</dd>
            <dt>{t('cityArea')}</dt><dd>{[b.location.city, b.location.area].filter(Boolean).join('، ')}</dd>
            <dt>{t('address')}</dt><dd>{b.location.address}</dd>
            <dt>{t('contact')}</dt><dd><a className="ltr" href={`tel:${b.host.phone}`}>{b.host.phone}</a></dd>
            {b.details.notes && <><dt>{t('notes')}</dt><dd>{b.details.notes}</dd></>}
            <dt>{t('provider')}</dt><dd>{b.provider?.displayName ?? '—'}</dd>
          </dl>
          <SectionTitle title={t('progress')} />
          <Timeline booking={b} viewer="COORDINATOR" />
        </div>
        <div className="panel" data-testid="coordinator-actions">
          <h2>{t('coordinatorActions')}</h2>
          {failure && <Alert kind="error">{failure}</Alert>}
          {allowed.size === 0 && <p className="privacy">{t('noActions')}</p>}
          {allowed.has('ACCEPT') && <Button onClick={() => act(x => api.transition(x.id, 'accept', x.version, note.trim() || undefined))} disabled={busy} testId="coord-accept">{t('accept')}</Button>}
          {allowed.has('ASSIGN_PROVIDER') && (
            <>
              <SelectField label={t('assignProvider')} value={providerId} onChange={e => setProviderId(e.target.value)}
                options={[{value: '', label: t('chooseProvider')}, ...(providers.data ?? []).map(p => ({value: p.id, label: p.displayName}))]} />
              <Button variant="secondary" onClick={() => act(x => api.assign(x.id, x.version, providerId))} disabled={busy || !providerId} testId="coord-assign">{t('assignProvider')}</Button>
            </>
          )}
          {allowed.has('CONFIRM') && <Button onClick={() => act(x => api.transition(x.id, 'confirm', x.version))} disabled={busy} testId="coord-confirm">{t('confirmAsCoordinator')}</Button>}
          {allowed.has('COMPLETE') && <Button onClick={() => act(x => api.transition(x.id, 'complete', x.version))} disabled={busy} testId="coord-complete">{t('complete')}</Button>}
          {reasonRequired && (
            <>
              <TextAreaField label={allowed.has('REJECT') ? t('rejectReason') : t('coordinatorCancelReason')} value={note} onChange={e => setNote(e.target.value)} maxLength={500} />
              {allowed.has('REJECT') && <Button variant="danger" onClick={() => act(x => api.transition(x.id, 'reject', x.version, note.trim()))} disabled={busy || note.trim().length < 3} testId="coord-reject">{t('reject')}</Button>}
              {allowed.has('CANCEL') && <Button variant="ghost" onClick={() => act(x => api.transition(x.id, 'cancel', x.version, note.trim()))} disabled={busy || note.trim().length < 3} testId="coord-cancel">{t('cancelRequest')}</Button>}
            </>
          )}
          {b.provider && (
            <>
              <SectionTitle title={t('providerSchedule')} />
              {schedule.data && schedule.data.length === 0 && <p className="privacy">{t('noSlots')}</p>}
              {schedule.data?.map(slot => <p key={slot.bookingId} className="privacy"><span className="ltr">{slot.trackingCode}</span> · {formatDateTime(slot.startsAt, locale)}</p>)}
            </>
          )}
          <Button variant="ghost" onClick={() => navigate('/rozeh/coordination')}>{t('back')}</Button>
        </div>
      </div>
    </CoordinationLayout>
  );
}
