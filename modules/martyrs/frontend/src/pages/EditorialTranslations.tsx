import {useEffect, useState} from 'react';
import {Alert, Button, Field, Loading, SelectField, TextAreaField} from '@haramyar/design-system';
import {api, type BookLocale, type TranslationBody} from '../api.ts';
import {useEnv, useLoad} from '../env.tsx';
import type {MessageKey} from '../messages.ts';

const LOCALES: {value: BookLocale; label: string}[] = [
  {value: 'fa', label: 'فارسی'}, {value: 'ar', label: 'العربية'}, {value: 'en', label: 'English'},
];

const empty = (): TranslationBody => ({fullName: '', honorific: '', shortDescription: '', biography: '', seoTitle: '', seoDescription: ''});

/** Profile prose translations follow their own review workflow; a language switch never fabricates translated prose. */
export function EditorialTranslations({profileId, sourceLocale}: {profileId: string; sourceLocale: string}) {
  const {t, can, route, errorText} = useEnv();
  const translations = useLoad(() => api.profileTranslations(profileId), [profileId]);
  const [locale, setLocale] = useState<BookLocale>(sourceLocale === 'fa' ? 'ar' : 'fa');
  const [form, setForm] = useState<TranslationBody>(empty);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const available = LOCALES.filter(option => option.value !== sourceLocale);
  const selectedLocale = locale === sourceLocale ? available[0]!.value : locale;
  const translation = translations.data?.find(entry => entry.locale === selectedLocale);

  useEffect(() => {
    setForm(translation ? {fullName: translation.fullName, honorific: translation.honorific ?? '', shortDescription: translation.shortDescription,
      biography: translation.biography, seoTitle: translation.seoTitle ?? '', seoDescription: translation.seoDescription ?? ''} : empty());
  }, [selectedLocale, translation?.version]);

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true); setMessage(null);
    try {await action(); translations.reload();}
    catch (error) {setMessage(errorText(error, route));}
    finally {setBusy(false);}
  };
  const set = (field: keyof TranslationBody, value: string) => setForm(previous => ({...previous, [field]: value}));
  const draft = !translation || translation.status === 'DRAFT';

  return <section className="panel" style={{marginBottom: 12}}>
    <h2>{t('profileTranslations')}</h2>
    <p className="privacy">{t('profileTranslationsHint')}</p>
    {translations.loading && !translations.data && <Loading label={t('loading')} />}
    {!!translations.error && <Alert kind="error">{errorText(translations.error, route)}</Alert>}
    <SelectField label={t('contentLocale')} value={selectedLocale} onChange={e => setLocale(e.target.value as BookLocale)} options={available} />
    {translation && <p>{t('status')}: {t(`status_${translation.status}` as MessageKey)}</p>}
    <fieldset disabled={busy || !draft} style={{border: 0, padding: 0}}>
      <Field label={t('fullName')} value={form.fullName} onChange={e => set('fullName', e.target.value)} maxLength={160} />
      <Field label={t('honorific')} value={form.honorific ?? ''} onChange={e => set('honorific', e.target.value)} maxLength={80} />
      <TextAreaField label={t('shortDescription')} value={form.shortDescription} onChange={e => set('shortDescription', e.target.value)} maxLength={300} rows={3} />
      <TextAreaField label={t('biographyText')} value={form.biography} onChange={e => set('biography', e.target.value)} maxLength={50000} rows={12} />
      <Field label={t('seoTitle')} value={form.seoTitle ?? ''} onChange={e => set('seoTitle', e.target.value)} maxLength={70} />
      <TextAreaField label={t('seoDescription')} value={form.seoDescription ?? ''} onChange={e => set('seoDescription', e.target.value)} maxLength={170} rows={2} />
      {draft && can('martyrs.editorial', 'edit') && <Button variant="secondary"
        disabled={busy || form.fullName.trim().length < 2 || form.shortDescription.trim().length < 2 || form.biography.trim().length < 20}
        onClick={() => void run(() => api.saveProfileTranslation(profileId, selectedLocale, {...form, expectedVersion: translation?.version}))}>{t('save')}</Button>}
    </fieldset>
    {translation?.status === 'DRAFT' && can('martyrs.editorial', 'submit') && <Button disabled={busy}
      onClick={() => void run(() => api.transitionProfileTranslation(profileId, selectedLocale, 'submit', translation.version))}>{t('submit')}</Button>}
    {translation?.status === 'IN_REVIEW' && can('martyrs.editorial', 'publish') && <Button disabled={busy}
      onClick={() => void run(() => api.transitionProfileTranslation(profileId, selectedLocale, 'publish', translation.version))}>{t('publish')}</Button>}
    {message && <Alert kind="error">{message}</Alert>}
  </section>;
}
