import {useEffect, useState} from 'react';
import {Alert, Brand, Button, Field, Icon, SelectField, StatusBadge, type ThemeMode} from '@haramyar/design-system';
import {formatDateTime} from '@haramyar/i18n';
import {can, http} from '../hive.ts';
import {navigate} from '../router.ts';
import {useShell} from '../shell.tsx';
import {SlotState} from '../components/ModuleHost.tsx';

interface OccasionRow {
  id: string; key: string; theme: 'DEFAULT' | 'CELEBRATION' | 'MOURNING'; titles: Record<string, string>; subtitles: Record<string, string>;
  startsAt: string; endsAt: string; priority: number; status: 'DRAFT' | 'SCHEDULED' | 'ARCHIVED'; version: number;
}

const BASE = '/api/routes/haramyar/admin/occasions';

/**
 * Occasion and theme administration (business administrators with haramyar-app.occasions). Windows are explicit
 * dates entered by the administrator; no lunar-calendar date is computed automatically. Themes change presentation only.
 */
export function OccasionAdmin() {
  const {t, context, locale, theme, previewTheme} = useShell();
  const [rows, setRows] = useState<OccasionRow[] | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [form, setForm] = useState({key: '', theme: 'CELEBRATION', titleFa: '', titleAr: '', titleEn: '', subtitleFa: '', startsAt: '', endsAt: '', priority: '10'});
  const allowed = can(context, 'haramyar-app.occasions', 'view');
  const manage = can(context, 'haramyar-app.occasions', 'configure');
  const load = () => http.get<OccasionRow[]>(BASE).then(setRows).catch(error => setFailure(String(error?.message ?? error)));
  useEffect(() => { if (allowed) void load(); return () => previewTheme(null); }, [allowed]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!context.authenticated || !allowed) return <SlotState icon="lock" title={context.authenticated ? t('accessDenied') : t('signInRequired')} hint={context.authenticated ? t('accessDeniedHint') : t('signInRequiredHint')} />;

  const act = async (run: () => Promise<unknown>) => {
    setFailure(null);
    try { await run(); await load(); } catch (error) { setFailure(error instanceof Error ? error.message : String(error)); }
  };
  const create = () => act(() => http.post(BASE, {
    key: form.key.trim(), theme: form.theme, priority: Number(form.priority) || 0,
    titles: {fa: form.titleFa.trim(), ...(form.titleAr.trim() ? {ar: form.titleAr.trim()} : {}), ...(form.titleEn.trim() ? {en: form.titleEn.trim()} : {})},
    subtitles: form.subtitleFa.trim() ? {fa: form.subtitleFa.trim()} : {},
    startsAt: new Date(form.startsAt).toISOString(), endsAt: new Date(form.endsAt).toISOString(),
  }).then(() => setForm(f => ({...f, key: '', titleFa: '', titleAr: '', titleEn: '', subtitleFa: ''}))));

  const options: {key: ThemeMode; label: string}[] = [{key: 'default', label: t('defaultTheme')}, {key: 'celebration', label: t('celebrationTheme')}, {key: 'mourning', label: t('mourningTheme')}];
  return (
    <div className="admin-page">
      <aside>
        <Brand name={t('brandName')} tagline={t('brandTagline')} onClick={() => navigate('/')} />
        <nav><button className="active"><Icon name="spark" /> {t('occasionAdmin')}</button></nav>
        <button onClick={() => navigate('/account')}><Icon name="arrow" /> {t('back')}</button>
      </aside>
      <main>
        <div className="admin-heading"><div><small>{t('roleAccess')}</small><h1>{t('occasionAdmin')}</h1><p>{t('occasionAdminHint')}</p></div></div>
        <section className="theme-switcher" aria-label={t('theme')}>
          <div className="switcher-label"><Icon name="spark" size={17} /><span>{t('theme')}</span></div>
          <div className="theme-options">{options.map(o => <button key={o.key} className={theme === o.key ? 'active' : ''} onClick={() => previewTheme(o.key)}>{o.label}</button>)}</div>
        </section>
        <section className="theme-preview-card">
          <div><span className="status active">{t('livePreview')}</span><h2>{t('everydayTitle')}</h2><p>{t('brandTagline')}</p></div>
          <div className={`preview-icon ${theme}`}><img src={theme === 'mourning' ? '/assets/brand/haramyar-app-icon-mourning.webp' : '/assets/brand/haramyar-app-icon-192.png'} alt="" /></div>
        </section>
        {failure && <Alert kind="error">{failure}</Alert>}
        {manage && (
          <form className="panel" style={{marginBottom: 18}} onSubmit={e => { e.preventDefault(); void create(); }}>
            <div className="field-row">
              <Field label={t('occasionKey')} value={form.key} onChange={e => setForm(f => ({...f, key: e.target.value.toLowerCase()}))} dir="ltr" pattern="[a-z][a-z0-9-]{2,59}" required />
              <SelectField label={t('theme')} value={form.theme} onChange={e => setForm(f => ({...f, theme: e.target.value}))} options={[{value: 'CELEBRATION', label: t('celebrationTheme')}, {value: 'MOURNING', label: t('mourningTheme')}, {value: 'DEFAULT', label: t('defaultTheme')}]} />
            </div>
            <Field label={t('titleFa')} value={form.titleFa} onChange={e => setForm(f => ({...f, titleFa: e.target.value}))} maxLength={120} required />
            <div className="field-row">
              <Field label={t('titleAr')} value={form.titleAr} onChange={e => setForm(f => ({...f, titleAr: e.target.value}))} maxLength={120} />
              <Field label={t('titleEn')} value={form.titleEn} onChange={e => setForm(f => ({...f, titleEn: e.target.value}))} maxLength={120} dir="ltr" />
            </div>
            <Field label={t('subtitleFa')} value={form.subtitleFa} onChange={e => setForm(f => ({...f, subtitleFa: e.target.value}))} maxLength={300} />
            <div className="field-row">
              <Field label={t('occasionStart')} type="datetime-local" value={form.startsAt} onChange={e => setForm(f => ({...f, startsAt: e.target.value}))} dir="ltr" required />
              <Field label={t('occasionEnd')} type="datetime-local" value={form.endsAt} onChange={e => setForm(f => ({...f, endsAt: e.target.value}))} dir="ltr" required />
            </div>
            <Field label={t('occasionPriority')} type="number" min={0} max={1000} value={form.priority} onChange={e => setForm(f => ({...f, priority: e.target.value}))} dir="ltr" />
            <Button type="submit" icon="plus" testId="create-occasion">{t('createOccasion')}</Button>
          </form>
        )}
        <section className="occasion-list">
          {rows?.map(row => (
            <article key={row.id} data-occasion={row.key}>
              <div className="occasion-date"><Icon name="calendar" /><small>#</small><b>{row.priority}</b></div>
              <div>
                <StatusBadge status={row.status === 'SCHEDULED' ? 'scheduled' : row.status === 'DRAFT' ? 'draft' : 'archived'} label={t(`occasion_${row.status}`)} />
                <h3>{row.titles[locale] ?? row.titles.fa}</h3>
                <p>{row.theme} · {formatDateTime(row.startsAt, locale)} → {formatDateTime(row.endsAt, locale)}</p>
              </div>
              <div className="occasion-actions">
                {manage && row.status === 'DRAFT' && <button aria-label={t('scheduleOccasion')} title={t('scheduleOccasion')} onClick={() => act(() => http.post(`${BASE}/${row.id}/schedule`))}><Icon name="check" /></button>}
                {manage && row.status !== 'ARCHIVED' && <button aria-label={t('archiveOccasion')} title={t('archiveOccasion')} onClick={() => act(() => http.post(`${BASE}/${row.id}/archive`))}><Icon name="trash" /></button>}
              </div>
            </article>
          ))}
        </section>
        <section className="scheduling-note"><Icon name="check" /><div><b>{t('occasionAdmin')}</b><p>{t('occasionSafety')} {t('timeZoneNote')} <span className="ltr">{Intl.DateTimeFormat().resolvedOptions().timeZone}</span></p></div></section>
      </main>
    </div>
  );
}
