import {useEffect, useState} from 'react';
import {Alert, Button, Field, Loading, SectionTitle, SelectField, StatusBadge, TextAreaField} from '@haramyar/design-system';
import {formatDateTime} from '@haramyar/i18n';
import {api, previewMediaUrl, type Calendar, type ContentBody, type EditorialProfile as Profile, type Source} from '../api.ts';
import {useEnv, useLoad} from '../env.tsx';
import type {MessageKey} from '../messages.ts';
import {EditorialLayout} from './EditorialList.tsx';
import {EditorialBooks} from './BookLibrary.tsx';
import {EditorialTranslations} from './EditorialTranslations.tsx';

const CALENDARS: Calendar[] = ['SOLAR_HIJRI', 'LUNAR_HIJRI', 'GREGORIAN'];
const KINDS: Source['kind'][] = ['ARCHIVE', 'BOOK', 'INTERVIEW', 'ARTICLE', 'WEBSITE', 'OTHER'];

interface EventForm {year: string; month: string; day: string; calendar: Calendar; place: string}
const eventForm = (e?: {date?: {year: number; month?: number; day?: number; calendar: Calendar}; place?: string}): EventForm =>
  ({year: e?.date?.year ? String(e.date.year) : '', month: e?.date?.month ? String(e.date.month) : '', day: e?.date?.day ? String(e.date.day) : '', calendar: e?.date?.calendar ?? 'SOLAR_HIJRI', place: e?.place ?? ''});
const eventBody = (f: EventForm) => {
  const date = f.year ? {year: Number(f.year), month: f.month ? Number(f.month) : undefined, day: f.day ? Number(f.day) : undefined, calendar: f.calendar} : undefined;
  return date || f.place.trim() ? {date, place: f.place.trim() || undefined} : undefined;
};

export function EditorialProfile() {
  const {t, locale, params, route, errorText, can, navigate} = useEnv();
  const id = params.profileId ?? '';
  const loaded = useLoad(() => api.get(id), [id]);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [form, setForm] = useState<{slug: string; fullName: string; honorific: string; contentLocale: string; shortDescription: string; biography: string; seoTitle: string; seoDescription: string; birth: EventForm; martyrdom: EventForm} | null>(null);
  const [source, setSource] = useState({kind: 'ARCHIVE' as Source['kind'], title: '', author: '', publisher: '', year: '', url: '', note: ''});
  const [story, setStory] = useState({id: '', title: '', body: '', sourceIds: [] as string[]});
  const [file, setFile] = useState<File | null>(null);
  const [usage, setUsage] = useState<'PORTRAIT' | 'GALLERY'>('PORTRAIT');
  const [caption, setCaption] = useState('');
  const [credit, setCredit] = useState('');
  const [note, setNote] = useState('');
  const [message, setMessage] = useState<{kind: 'error' | 'success'; text: string} | null>(null);
  const [busy, setBusy] = useState(false);

  const adopt = (p: Profile) => {
    setProfile(p);
    setForm({slug: p.slug, fullName: p.content.name.fullName, honorific: p.content.name.honorific ?? '', contentLocale: p.content.contentLocale,
      shortDescription: p.content.shortDescription ?? '', biography: p.content.biography.text ?? '', seoTitle: p.content.seo.title ?? '',
      seoDescription: p.content.seo.description ?? '', birth: eventForm(p.content.birth), martyrdom: eventForm(p.content.martyrdom)});
  };
  useEffect(() => { if (loaded.data) adopt(loaded.data); }, [loaded.data]);

  const run = async (action: (p: Profile) => Promise<Profile>, success?: string) => {
    if (!profile) return;
    setBusy(true);
    setMessage(null);
    try {
      adopt(await action(profile));
      if (success) setMessage({kind: 'success', text: success});
      setNote('');
    } catch (error) {
      setMessage({kind: 'error', text: errorText(error, route)});
      loaded.reload();
    } finally {
      setBusy(false);
    }
  };

  if (!profile || !form) return <EditorialLayout>{loaded.error ? <Alert kind="error">{errorText(loaded.error, route)}</Alert> : <Loading label={t('loading')} />}</EditorialLayout>;

  const draft = profile.status === 'DRAFT';
  const contentBody = (): ContentBody => ({fullName: form.fullName.trim(), honorific: form.honorific.trim() || undefined, contentLocale: form.contentLocale,
    shortDescription: form.shortDescription.trim() || undefined, biography: form.biography.trim() || undefined, birth: eventBody(form.birth), martyrdom: eventBody(form.martyrdom),
    seoTitle: form.seoTitle.trim() || undefined, seoDescription: form.seoDescription.trim() || undefined});
  const set = (key: keyof typeof form, value: string) => setForm(f => f && ({...f, [key]: value}));
  const setEvent = (which: 'birth' | 'martyrdom', key: keyof EventForm, value: string) => setForm(f => f && ({...f, [which]: {...f[which], [key]: value}}));
  const eventFields = (which: 'birth' | 'martyrdom', label: string) => (
    <fieldset className="panel" style={{marginBottom: 12}} disabled={!draft}>
      <h2>{label}</h2>
      <div className="field-row">
        <Field label={t('year')} value={form[which].year} onChange={e => setEvent(which, 'year', e.target.value.replace(/\D/g, ''))} inputMode="numeric" dir="ltr" />
        <SelectField label={t('calendar')} value={form[which].calendar} onChange={e => setEvent(which, 'calendar', e.target.value)} options={CALENDARS.map(c => ({value: c, label: t(`cal_${c}` as MessageKey)}))} />
      </div>
      <div className="field-row">
        <Field label={t('month')} optionalLabel={t('optional')} value={form[which].month} onChange={e => setEvent(which, 'month', e.target.value.replace(/\D/g, ''))} inputMode="numeric" dir="ltr" />
        <Field label={t('day')} optionalLabel={t('optional')} value={form[which].day} onChange={e => setEvent(which, 'day', e.target.value.replace(/\D/g, ''))} inputMode="numeric" dir="ltr" />
      </div>
      <Field label={t('place')} optionalLabel={t('optional')} value={form[which].place} onChange={e => setEvent(which, 'place', e.target.value)} maxLength={160} />
    </fieldset>
  );

  return (
    <EditorialLayout>
      <div className="admin-heading">
        <div><small>{t('editorial')}</small><h1>{profile.content.name.fullName}</h1><p className="ltr">{profile.slug}</p></div>
        <StatusBadge status={profile.status} label={t(`status_${profile.status}` as MessageKey)} />
      </div>
      {message && <Alert kind={message.kind}>{message.text}</Alert>}
      {!draft && <Alert kind="info">{t('readOnly')}</Alert>}
      <div className="detail-grid">
        <div>
          <form className="panel" onSubmit={e => { e.preventDefault(); void run(p => api.revise(p.id, p.version, form.slug.trim(), contentBody()), t('saved')); }} style={{marginBottom: 12}}>
            <fieldset disabled={!draft || busy} style={{border: 0, padding: 0, margin: 0}}>
              <Field label={t('fullName')} value={form.fullName} onChange={e => set('fullName', e.target.value)} maxLength={160} name="fullName" />
              <div className="field-row">
                <Field label={t('honorific')} optionalLabel={t('optional')} value={form.honorific} onChange={e => set('honorific', e.target.value)} maxLength={80} />
                <SelectField label={t('contentLocale')} value={form.contentLocale} onChange={e => set('contentLocale', e.target.value)} options={[{value: 'fa', label: 'فارسی'}, {value: 'ar', label: 'العربية'}, {value: 'en', label: 'English'}]} />
              </div>
              <Field label={t('slug')} hint={t('slugHint')} value={form.slug} onChange={e => set('slug', e.target.value.toLowerCase())} dir="ltr" disabled={!!profile.firstPublishedAt || !draft} />
              <TextAreaField label={t('shortDescription')} value={form.shortDescription} onChange={e => set('shortDescription', e.target.value)} maxLength={300} name="shortDescription" />
              <TextAreaField label={t('biographyText')} hint={t('biographyHint')} value={form.biography} onChange={e => set('biography', e.target.value)} rows={12} maxLength={50000} name="biography" />
              <div className="field-row">
                <Field label={t('seoTitle')} optionalLabel={t('optional')} value={form.seoTitle} onChange={e => set('seoTitle', e.target.value)} maxLength={70} />
                <Field label={t('seoDescription')} optionalLabel={t('optional')} value={form.seoDescription} onChange={e => set('seoDescription', e.target.value)} maxLength={170} />
              </div>
              {draft && <Button type="submit" disabled={busy || form.fullName.trim().length < 2} testId="save-profile">{t('save')}</Button>}
            </fieldset>
          </form>
          {eventFields('birth', t('born'))}
          {eventFields('martyrdom', t('martyred'))}
          <EditorialTranslations profileId={profile.id} sourceLocale={profile.content.contentLocale} />
          <EditorialBooks profileId={profile.id} />
          <section className="panel" style={{marginBottom: 12}}>
            <h2>{t('sources')}</h2>
            <ul className="sources" style={{paddingInlineStart: 0}}>
              {profile.sources.map(s => (
                <li key={s.id}><b>{s.title}</b> <small>({t(`kind_${s.kind}` as MessageKey)})</small>
                  {draft && <button className="btn btn-ghost" style={{minHeight: 32, marginInlineStart: 8}} disabled={busy} onClick={() => run(p => api.removeSource(p.id, p.version, s.id))}>{t('remove')}</button>}</li>
              ))}
            </ul>
            {draft && (
              <form onSubmit={e => { e.preventDefault(); void run(p => api.addSource(p.id, p.version, {kind: source.kind, title: source.title.trim(), author: source.author.trim() || undefined, publisher: source.publisher.trim() || undefined,
                year: source.year ? Number(source.year) : undefined, url: source.url.trim() || undefined, note: source.note.trim() || undefined})).then(() => setSource(s => ({...s, title: '', author: '', publisher: '', year: '', url: '', note: ''}))); }}>
                <div className="field-row">
                  <SelectField label={t('kind')} value={source.kind} onChange={e => setSource(s => ({...s, kind: e.target.value as Source['kind']}))} options={KINDS.map(k => ({value: k, label: t(`kind_${k}` as MessageKey)}))} />
                  <Field label={t('sourceTitle')} value={source.title} onChange={e => setSource(s => ({...s, title: e.target.value}))} maxLength={300} name="sourceTitle" />
                </div>
                <div className="field-row">
                  <Field label={t('author')} optionalLabel={t('optional')} value={source.author} onChange={e => setSource(s => ({...s, author: e.target.value}))} />
                  <Field label={t('publisher')} optionalLabel={t('optional')} value={source.publisher} onChange={e => setSource(s => ({...s, publisher: e.target.value}))} />
                </div>
                <div className="field-row">
                  <Field label={t('year')} optionalLabel={t('optional')} value={source.year} onChange={e => setSource(s => ({...s, year: e.target.value.replace(/\D/g, '')}))} dir="ltr" />
                  <Field label={t('url')} optionalLabel={t('optional')} value={source.url} onChange={e => setSource(s => ({...s, url: e.target.value}))} dir="ltr" type="url" />
                </div>
                <Button type="submit" variant="secondary" disabled={busy || source.title.trim().length < 2} icon="plus" testId="add-source">{t('addSource')}</Button>
              </form>
            )}
          </section>
          <section className="panel" style={{marginBottom: 12}}>
            <h2>{t('stories')}</h2>
            {profile.stories.map(s => (
              <div key={s.id} className="inline-story" style={{marginBottom: 8}}>
                <span>{t('story')}</span><b>{s.title}</b>
                {draft && <small><button className="btn btn-ghost" style={{minHeight: 30}} onClick={() => setStory({id: s.id, title: s.title, body: s.body, sourceIds: s.sourceIds})}>{t('open')}</button>
                  <button className="btn btn-ghost" style={{minHeight: 30}} disabled={busy} onClick={() => run(p => api.removeStory(p.id, p.version, s.id))}>{t('remove')}</button></small>}
              </div>
            ))}
            {draft && (
              <form onSubmit={e => { e.preventDefault(); void run(p => story.id ? api.updateStory(p.id, p.version, story) : api.addStory(p.id, p.version, {title: story.title.trim(), body: story.body.trim(), sourceIds: story.sourceIds}))
                .then(() => setStory({id: '', title: '', body: '', sourceIds: []})); }}>
                <Field label={t('storyTitle')} value={story.title} onChange={e => setStory(s => ({...s, title: e.target.value}))} maxLength={200} />
                <TextAreaField label={t('storyBody')} value={story.body} onChange={e => setStory(s => ({...s, body: e.target.value}))} rows={6} maxLength={20000} />
                {profile.sources.length > 0 && <div className="field"><span>{t('citedSources')}</span><div className="chip-list">{profile.sources.map(src => (
                  <label key={src.id} className="checkbox"><input type="checkbox" checked={story.sourceIds.includes(src.id)} onChange={e => setStory(s => ({...s, sourceIds: e.target.checked ? [...s.sourceIds, src.id] : s.sourceIds.filter(x => x !== src.id)}))} />{src.title}</label>
                ))}</div></div>}
                <Button type="submit" variant="secondary" disabled={busy || story.title.trim().length < 2 || story.body.trim().length < 20}>{story.id ? t('update') : t('addStory')}</Button>
              </form>
            )}
          </section>
        </div>
        <div>
          <section className="panel" style={{marginBottom: 12}} data-testid="workflow">
            <h2>{t('workflow')}</h2>
            {draft && can('martyrs.editorial', 'submit') && <Button onClick={() => run(p => api.transition(p.id, 'submit', p.version))} disabled={busy} testId="submit-review">{t('submit')}</Button>}
            {profile.status === 'IN_REVIEW' && can('martyrs.editorial', 'review') && <>
              <TextAreaField label={t('reviewNote')} value={note} onChange={e => setNote(e.target.value)} maxLength={1000} />
              <Button variant="ghost" onClick={() => run(p => api.transition(p.id, 'request-changes', p.version, note.trim()))} disabled={busy || note.trim().length < 3} testId="request-changes">{t('requestChanges')}</Button>
            </>}
            {profile.status === 'IN_REVIEW' && can('martyrs.editorial', 'publish') && <>
              {profile.submittedByViewer && <p className="privacy">{t('fourEyes')}</p>}
              <Button onClick={() => run(p => api.transition(p.id, 'publish', p.version))} disabled={busy} testId="publish">{t('publish')}</Button>
            </>}
            {profile.status === 'PUBLISHED' && can('martyrs.editorial', 'publish') && <>
              <TextAreaField label={t('archiveReason')} optionalLabel={t('optional')} value={note} onChange={e => setNote(e.target.value)} maxLength={500} />
              <Button variant="danger" onClick={() => run(p => api.transition(p.id, 'archive', p.version, note.trim() || undefined))} disabled={busy} testId="archive">{t('archive')}</Button>
            </>}
            {profile.status === 'PUBLISHED' && <Button variant="ghost" onClick={() => navigate(`/martyrs/profiles/${profile.slug}`)}>{t('viewPublic')}</Button>}
            {profile.status === 'ARCHIVED' && can('martyrs.editorial', 'edit') && <Button variant="ghost" onClick={() => run(p => api.transition(p.id, 'reopen', p.version))} disabled={busy}>{t('reopen')}</Button>}
          </section>
          <section className="panel" style={{marginBottom: 12}}>
            <h2>{t('media')}</h2>
            {profile.portrait && <figure className="gallery" style={{gridTemplateColumns: '1fr'}}><img src={previewMediaUrl(profile.portrait.mediaId)} alt={profile.portrait.caption ?? ''} /><figcaption>{t('portrait')}</figcaption></figure>}
            <div className="gallery">{profile.gallery.map(m => <figure key={m.mediaId}><img src={previewMediaUrl(m.mediaId)} alt={m.caption ?? ''} />
              {draft && <button className="btn btn-ghost" style={{minHeight: 30}} disabled={busy} onClick={() => run(p => api.removeMedia(p.id, p.version, m.mediaId))}>{t('remove')}</button>}</figure>)}</div>
            {draft && (
              <form onSubmit={e => { e.preventDefault(); if (file) void run(p => api.upload(p.id, p.version, file, usage, caption.trim() || undefined, credit.trim() || undefined)).then(() => { setFile(null); setCaption(''); setCredit(''); }); }}>
                <SelectField label={t('media')} value={usage} onChange={e => setUsage(e.target.value as 'PORTRAIT' | 'GALLERY')} options={[{value: 'PORTRAIT', label: t('portrait')}, {value: 'GALLERY', label: t('galleryImage')}]} />
                <label className="field"><span>{t('upload')}</span><input type="file" accept="image/jpeg,image/png,image/webp" onChange={e => setFile(e.target.files?.[0] ?? null)} data-testid="media-file" /><small className="hint">{t('mediaRules')}</small></label>
                <Field label={t('caption')} optionalLabel={t('optional')} value={caption} onChange={e => setCaption(e.target.value)} maxLength={300} />
                <Field label={t('credit')} optionalLabel={t('optional')} value={credit} onChange={e => setCredit(e.target.value)} maxLength={200} />
                <Button type="submit" variant="secondary" icon="upload" disabled={busy || !file}>{t('upload')}</Button>
              </form>
            )}
          </section>
          <section className="panel">
            <SectionTitle title={t('history')} />
            <div className="timeline">{profile.history.map((h, i) => (
              <div key={i} className="done"><i /><p><b>{t(`op_${h.operation}` as MessageKey)}</b><small>{formatDateTime(h.at, locale)} · {h.byViewer ? t('byYou') : t(`role_${h.role}` as MessageKey)}</small>{h.note && <small className="note">{h.note}</small>}</p></div>
            ))}</div>
          </section>
        </div>
      </div>
    </EditorialLayout>
  );
}
