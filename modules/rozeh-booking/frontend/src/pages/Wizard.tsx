import {useEffect, useMemo, useState} from 'react';
import {Alert, Button, ChoiceRow, Field, Icon, InfoCard, ModuleBar, SelectField, Summary, TextAreaField} from '@haramyar/design-system';
import {compareDates, cursorFor, monthGrid, normalizeMobile, sameDate, shiftMonth, todayIn, toLatinDigits, zonedIso} from '@haramyar/i18n';
import {api, errorCode, isUnauthenticated, login, uuid, type Catalog} from '../api.ts';
import {useEnv, useLoad} from '../env.tsx';
import {codeLabel} from '../labels.ts';
import type {MessageKey} from '../messages.ts';
import {alignWithCatalog, catalogChoices, fieldErrorFor, firstInvalidStep, validate, type Draft, type Errors} from '../wizard-rules.ts';

const DRAFT_KEY = 'haramyar.rozeh.draft';
const DRAFT_TTL_MS = 30 * 60 * 1000;
const DURATIONS = [30, 45, 60, 90, 120, 180];
const ZONES = ['Asia/Tehran', 'Asia/Baghdad'];

/**
 * The in-progress request survives the sign-in redirect in sessionStorage (this tab only, 30 minutes, removed on submit
 * and on sign-out). Nothing is sent to the server before the user submits.
 */
function loadDraft(zone: string): Draft {
  try {
    const stored = JSON.parse(sessionStorage.getItem(DRAFT_KEY) ?? 'null') as Draft | null;
    if (stored && Date.now() - stored.savedAt < DRAFT_TTL_MS) return stored;
  } catch { /* unavailable or malformed */ }
  return {hostName: '', hostPhone: '', city: '', area: '', address: '', category: 'rozeh', audience: null, capacity: '', notes: '', date: null,
    time: '18:00', duration: 60, zone, clientRequestId: uuid(), step: 1, savedAt: Date.now()};
}

function saveDraft(draft: Draft) {
  try { sessionStorage.setItem(DRAFT_KEY, JSON.stringify({...draft, savedAt: Date.now()})); } catch { /* storage unavailable */ }
}

export function clearDraft() {
  try { sessionStorage.removeItem(DRAFT_KEY); } catch { /* storage unavailable */ }
}

export function Wizard() {
  const {t, navigate, locale, authenticated, errorText, zone} = useEnv();
  const [draft, setDraft] = useState<Draft>(() => loadDraft(zone));
  const [errors, setErrors] = useState<Errors>({});
  const [submitting, setSubmitting] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const catalog = useLoad<Catalog>(() => api.catalog(), []);
  const [cursor, setCursor] = useState(() => cursorFor(draft.date ?? todayIn(draft.zone), locale));
  const today = todayIn(draft.zone);
  const grid = useMemo(() => monthGrid(cursor, locale), [cursor, locale]);

  useEffect(() => { saveDraft(draft); }, [draft]);
  useEffect(() => { if (catalog.data) setDraft(d => alignWithCatalog(d, catalog.data!)); }, [catalog.data]);

  const update = <K extends keyof Draft>(key: K, value: Draft[K]) => setDraft(d => ({...d, [key]: value}));
  const err = (key: keyof Draft) => (errors[key] ? t(errors[key]!) : null);
  // Moves to a step; errors are cleared only on ordinary navigation, never when showing why a submission was refused.
  const show = (step: number, found: Errors = {}) => { setErrors(found); setFailure(null); update('step', step); globalThis.scrollTo?.({top: 0}); };
  const go = (step: number) => show(step);
  const next = () => {
    const found = validate(draft, draft.step);
    setErrors(found);
    if (Object.keys(found).length === 0) go(draft.step + 1);
  };
  const back = () => (draft.step > 1 ? go(draft.step - 1) : navigate('/rozeh'));

  const submit = async () => {
    const found = validate(draft, 3);
    const invalid = firstInvalidStep(found);
    if (invalid) { show(invalid, found); return; }
    const choices = catalogChoices(draft, catalog.data);
    if (!choices) { setFailure(t('catalogFailed')); return; }
    if (!authenticated) { saveDraft({...draft, step: 4}); login('/rozeh/request'); return; }
    setSubmitting(true);
    setFailure(null);
    const [hour, minute] = draft.time.split(':').map(Number);
    const capacity = toLatinDigits(draft.capacity).trim();
    try {
      const booking = await api.submit({
        clientRequestId: draft.clientRequestId, hostName: draft.hostName.trim(), hostPhone: normalizeMobile(draft.hostPhone) ?? draft.hostPhone,
        city: draft.city.trim(), area: draft.area.trim() || undefined, address: draft.address.trim(), category: choices.category,
        audience: choices.audience, deliveryMode: choices.deliveryMode,
        approximateCapacity: capacity ? Number(capacity) : undefined, notes: draft.notes.trim() || undefined,
        startsAt: zonedIso(draft.date!, hour!, minute!, draft.zone), durationMinutes: draft.duration, timeZone: draft.zone,
      });
      clearDraft();
      try { sessionStorage.setItem('haramyar.rozeh.submitted', booking.id); } catch { /* storage unavailable */ }
      navigate(`/rozeh/requests/${booking.id}`);
    } catch (error) {
      if (isUnauthenticated(error)) { saveDraft({...draft, step: 4}); login('/rozeh/request'); return; }
      const field = fieldErrorFor(errorCode(error));
      if (field) { show(field.step, {[field.field]: field.key}); return; }
      setFailure(errorText(error));
    } finally {
      setSubmitting(false);
    }
  };

  // Choices come only from the loaded catalog; without it the wizard shows the failure and cannot submit.
  const audiences = (catalog.data?.audiences ?? []).map(code => ({value: code, label: codeLabel(t, 'audience', code)}));
  const categories = (catalog.data?.categories ?? []).map(code => ({value: code, label: codeLabel(t, 'category', code)}));
  const catalogFailure = catalog.error ? <Alert kind="error"><span data-testid="catalog-failed">{t('catalogFailed')}</span> <Button variant="secondary" onClick={catalog.reload} testId="catalog-retry">{t('retry')}</Button></Alert> : null;
  const ready = !!catalogChoices(draft, catalog.data);
  const dateLabel = draft.date ? new Intl.DateTimeFormat(locale === 'fa' ? 'fa-IR-u-ca-persian' : locale === 'ar' ? 'ar-IQ' : 'en-GB', {weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC'})
    .format(Date.UTC(draft.date.year, draft.date.month - 1, draft.date.day)) : '—';
  const endTime = (() => {
    const [h, m] = draft.time.split(':').map(Number);
    if (h === undefined || m === undefined || Number.isNaN(h)) return '';
    const total = (h * 60 + m + draft.duration) % (24 * 60);
    return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
  })();

  if (draft.step === 4) {
    return (
      <div className="module-page">
        <ModuleBar title={t('reviewRequest')} onBack={() => go(3)} backLabel={t('back')} />
        <div className="module-body review">
          <div className="review-head"><span><Icon name="check" /></span><h1>{t('reviewTitle')}</h1><p>{t('reviewHint')}</p></div>
          <Summary title={t('host')} editLabel={t('edit')} onEdit={() => go(1)} rows={[[t('hostName'), draft.hostName], [t('hostPhone'), <span className="ltr">{normalizeMobile(draft.hostPhone) ?? draft.hostPhone}</span>]]} />
          <Summary title={t('placeAndCeremony')} editLabel={t('edit')} onEdit={() => go(2)} rows={[
            [t('cityArea'), [draft.city, draft.area].filter(Boolean).join('، ')], [t('address'), draft.address], [t('category'), codeLabel(t, 'category', draft.category)],
            [t('audience'), codeLabel(t, 'audience', draft.audience)], [t('capacity'), draft.capacity || '—']]} />
          <Summary title={t('proposedTime')} editLabel={t('edit')} onEdit={() => go(3)} rows={[
            [t('date'), dateLabel], [t('time'), <span className="ltr">{draft.time} – {endTime}</span>], [t('timeZone'), t(`zone_${draft.zone.replace('/', '_')}` as MessageKey)]]} />
          <InfoCard icon="bell" title={t('notBookingTitle')} warning>{t('notBookingHint')}</InfoCard>
          {!authenticated && <Alert kind="info">{t('signInToSubmit')}</Alert>}
          {catalogFailure}
          {failure && <Alert kind="error">{failure}</Alert>}
          <Button onClick={submit} disabled={submitting || !ready} testId="submit-request">{submitting ? t('submitting') : authenticated ? t('submit') : t('signInAndSubmit')}</Button>
        </div>
      </div>
    );
  }

  return (
    <div className="module-page wizard">
      <ModuleBar title={t('wizardTitle')} onBack={back} backLabel={t('back')} />
      <div className="progress"><span>{t('stepOf', {step: new Intl.NumberFormat(locale === 'en' ? 'en' : locale === 'fa' ? 'fa-IR' : 'ar-IQ').format(draft.step)})}</span><div><i style={{width: `${draft.step * 33.33}%`}} /></div></div>
      <form className="form-wrap" noValidate onSubmit={e => { e.preventDefault(); next(); }}>
        {draft.step === 1 && <>
          <div className="form-title"><span><Icon name="user" /></span><div><h1>{t('hostTitle')}</h1><p>{t('hostHint')}</p></div></div>
          <Field label={t('hostName')} placeholder={t('hostNamePlaceholder')} value={draft.hostName} onChange={e => update('hostName', e.target.value)} error={err('hostName')} autoComplete="name" name="hostName" />
          <Field label={t('hostPhone')} hint={t('hostPhoneHint')} value={draft.hostPhone} onChange={e => update('hostPhone', e.target.value)} error={err('hostPhone')} inputMode="tel" autoComplete="tel" dir="ltr" name="hostPhone" />
          <div className="privacy"><Icon name="lock" /><span>{t('privacyNote')}</span></div>
        </>}
        {draft.step === 2 && <>
          <div className="form-title"><span><Icon name="map" /></span><div><h1>{t('locationTitle')}</h1><p>{t('locationHint')}</p></div></div>
          {catalogFailure}
          <div className="field-row">
            <Field label={t('city')} value={draft.city} onChange={e => update('city', e.target.value)} error={err('city')} autoComplete="address-level2" name="city" />
            <Field label={t('area')} optionalLabel={t('optional')} value={draft.area} onChange={e => update('area', e.target.value)} name="area" />
          </div>
          <Field label={t('address')} placeholder={t('addressPlaceholder')} value={draft.address} onChange={e => update('address', e.target.value)} error={err('address')} autoComplete="street-address" name="address" />
          {categories.length > 1
            ? <ChoiceRow label={t('category')} options={categories} value={draft.category} onChange={v => update('category', v ?? categories[0]!.value)} />
            : null}
          {audiences.length > 0 && <ChoiceRow label={t('audience')} optionalLabel={t('optional')} options={audiences} value={draft.audience} onChange={v => update('audience', v)} />}
          <Field label={t('capacity')} optionalLabel={t('optional')} placeholder={t('capacityPlaceholder')} value={draft.capacity} onChange={e => update('capacity', e.target.value)} error={err('capacity')} inputMode="numeric" name="capacity" />
          <TextAreaField label={t('notes')} optionalLabel={t('optional')} value={draft.notes} onChange={e => update('notes', e.target.value)} maxLength={1000} name="notes" />
        </>}
        {draft.step === 3 && <>
          <div className="form-title"><span><Icon name="calendar" /></span><div><h1>{t('timeTitle')}</h1><p>{t('timeHint')}</p></div></div>
          <div className="date-card" aria-label={t('pickDate')}>
            <div className="calendar-head">
              <button type="button" onClick={() => setCursor(c => shiftMonth(c, -1))} aria-label={t('prevMonth')}><Icon name="chevron" /></button>
              <b>{grid.title}</b>
              <button type="button" onClick={() => setCursor(c => shiftMonth(c, 1))} aria-label={t('nextMonth')} style={{transform: 'scaleX(-1)'}}><Icon name="chevron" /></button>
            </div>
            <div className="week">{grid.weekdays.map(d => <span key={d}>{d}</span>)}</div>
            <div className="days">
              {grid.days.map((day, i) => day
                ? <button type="button" key={i} data-date={`${day.date.year}-${day.date.month}-${day.date.day}`} disabled={compareDates(day.date, today) < 0}
                    className={sameDate(day.date, draft.date) ? 'selected' : ''} aria-pressed={sameDate(day.date, draft.date)} onClick={() => update('date', day.date)}>{day.label}</button>
                : <span key={i} />)}
            </div>
            {errors.date && <small className="error" role="alert" style={{color: 'var(--danger)', fontSize: 11}}>{t(errors.date)}</small>}
          </div>
          <div className="field-row">
            <Field label={t('startTime')} type="time" value={draft.time} onChange={e => update('time', e.target.value)} error={err('time')} dir="ltr" name="time" />
            <SelectField label={t('duration')} value={String(draft.duration)} onChange={e => update('duration', Number(e.target.value))}
              options={DURATIONS.map(n => ({value: String(n), label: t('minutes', {n: new Intl.NumberFormat(locale === 'en' ? 'en' : locale === 'fa' ? 'fa-IR' : 'ar-IQ').format(n)})}))} />
          </div>
          <SelectField label={t('timeZone')} value={draft.zone} onChange={e => update('zone', e.target.value)} options={ZONES.map(z => ({value: z, label: t(`zone_${z.replace('/', '_')}` as MessageKey)}))} />
        </>}
        <div className="sticky-action">
          <Button type="submit" icon="arrow" testId="wizard-next">{draft.step === 3 ? t('reviewRequest') : t('continue')}</Button>
          <small>{t('draftKept')}</small>
        </div>
      </form>
    </div>
  );
}
