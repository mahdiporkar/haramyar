import {useEffect, useState} from 'react';
import {Alert, Button, Field, Loading, SelectField, TextAreaField} from '@haramyar/design-system';
import {api, bookPdfUrl, type BookLocale, type EditorialBook, type PublicBook} from '../api.ts';
import {useEnv, useLoad} from '../env.tsx';
import type {MessageKey} from '../messages.ts';

const LANGUAGES: {value: BookLocale; label: string}[] = [
  {value: 'ar', label: 'العربية'}, {value: 'fa', label: 'فارسی'}, {value: 'en', label: 'English'},
];

export function PublicBooks({slug}: {slug: string}) {
  const {t, locale, errorText, route} = useEnv();
  const books = useLoad<PublicBook[]>(() => api.books(slug, locale), [slug, locale]);
  return <section className="books-section">
    <h2>{t('booksLibrary')}</h2>
    {books.loading && !books.data && <Loading label={t('loading')} />}
    {!!books.error && <Alert kind="error">{errorText(books.error, route)}</Alert>}
    {(['AUTHORED', 'ABOUT'] as const).map(kind => {
      const group = books.data?.filter(b => b.kind === kind) ?? [];
      return group.length > 0 && <div key={kind}>
        <h3>{kind === 'AUTHORED' ? t('booksAuthored') : t('booksAbout')}</h3>
        <div className="book-cards">{group.map(book => <div className="book-card" key={book.id}>
          <h4 lang={book.selectedEdition.locale}>{book.selectedEdition.title}</h4>
          {book.selectedEdition.summary && <p lang={book.selectedEdition.locale}>{book.selectedEdition.summary}</p>}
          <div className="book-editions">{book.availableEditions.map(e => <a key={e.id} href={bookPdfUrl(e.id)}
            target="_blank" rel="noopener noreferrer" lang={e.locale}>
            {t('readBook')} · {LANGUAGES.find(l => l.value === e.locale)?.label}
          </a>)}</div>
          <a className="book-source" href={book.sourceUrl} target="_blank" rel="nofollow noopener noreferrer">{t('bookSource')}</a>
        </div>)}</div>
      </div>;
    })}
  </section>;
}

function EditionEditor({book, refresh}: {book: EditorialBook; refresh: () => void}) {
  const {t, can, route, errorText} = useEnv();
  const [lang, setLang] = useState<BookLocale>('ar');
  const [title, setTitle] = useState('');
  const [summary, setSummary] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const edition = book.editions.find(e => e.locale === lang);
  useEffect(() => {
    setTitle(edition?.title ?? (lang === 'ar' ? book.originalTitle : ''));
    setSummary(edition?.summary ?? ''); setFile(null);
  }, [lang, book.originalTitle, edition?.title, edition?.summary]);
  const run = async (action: () => Promise<unknown>) => {
    setBusy(true); setMessage(null);
    try {await action(); refresh(); setFile(null);}
    catch (error) {setMessage(errorText(error, route));}
    finally {setBusy(false);}
  };
  return <div className="book-card">
    <h4>{book.originalTitle}</h4>
    <SelectField label={t('contentLocale')} value={lang} onChange={e => setLang(e.target.value as BookLocale)} options={LANGUAGES} />
    {edition && <small>{t('status')}: {t(`status_${edition.status}` as MessageKey)} · {edition.hasFile ? t('bookPdfReady') : t('bookPdfMissing')}</small>}
    <fieldset disabled={busy || !!edition && edition.status !== 'DRAFT'} style={{border: 0, padding: 0}}>
      <Field label={t('bookTitle')} value={title} onChange={e => setTitle(e.target.value)} maxLength={240} />
      <TextAreaField label={t('bookSummary')} value={summary} onChange={e => setSummary(e.target.value)} maxLength={4000} rows={3} />
      {can('martyrs.editorial', 'edit') && (!edition || edition.status === 'DRAFT') && <Button
        variant="secondary" disabled={busy || title.trim().length < 2}
        onClick={() => void run(() => api.saveBookEdition(book.id, lang, title.trim(), summary.trim(), edition?.version))}>{t('save')}</Button>}
      {edition?.status === 'DRAFT' && can('martyrs.editorial', 'edit') && <label className="field">
        <span>{t('bookUpload')}</span><input type="file" accept="application/pdf,.pdf" onChange={e => setFile(e.target.files?.[0] ?? null)} />
        <small className="hint">{t('bookUploadHint')}</small>
        <Button variant="secondary" disabled={busy || !file || file.size > 30 * 1024 * 1024} onClick={() => file && void run(() => api.uploadBookPdf(edition.id, edition.version, file))}>{t('upload')}</Button>
      </label>}
    </fieldset>
    {edition?.status === 'DRAFT' && edition.hasFile && can('martyrs.editorial', 'submit') && <Button
      disabled={busy} onClick={() => void run(() => api.transitionBook(edition.id, 'submit', edition.version))}>{t('submit')}</Button>}
    {edition?.status === 'IN_REVIEW' && can('martyrs.editorial', 'publish') && <Button
      disabled={busy} onClick={() => void run(() => api.transitionBook(edition.id, 'publish', edition.version))}>{t('publish')}</Button>}
    {message && <Alert kind="error">{message}</Alert>}
  </div>;
}

export function EditorialBooks({profileId}: {profileId: string}) {
  const {t, can, route, errorText} = useEnv();
  const books = useLoad<EditorialBook[]>(() => api.editorialBooks(profileId), [profileId]);
  const [kind, setKind] = useState<'AUTHORED' | 'ABOUT'>('AUTHORED');
  const [title, setTitle] = useState('');
  const [url, setUrl] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const create = async () => {
    setBusy(true); setMessage(null);
    try { await api.createBook(profileId, {kind, originalTitle: title.trim(), sourceUrl: url.trim()});
      setTitle(''); setUrl(''); books.reload(); }
    catch (error) {setMessage(errorText(error, route));}
    finally {setBusy(false);}
  };
  return <section className="panel books-section" style={{marginBlock: 12}}>
    <h2>{t('booksLibrary')}</h2>
    <p className="privacy">{t('booksEditorialHint')}</p>
    {books.loading && !books.data && <Loading label={t('loading')} />}
    {!!books.error && <Alert kind="error">{errorText(books.error, route)}</Alert>}
    {books.data?.map(book => <EditionEditor key={book.id} book={book} refresh={books.reload} />)}
    {can('martyrs.editorial', 'edit') && <form onSubmit={e => {e.preventDefault(); void create();}}>
      <h3>{t('newBook')}</h3>
      <SelectField label={t('kind')} value={kind} onChange={e => setKind(e.target.value as 'AUTHORED' | 'ABOUT')}
        options={[{value: 'AUTHORED', label: t('booksAuthored')}, {value: 'ABOUT', label: t('booksAbout')}]} />
      <Field label={t('bookTitle')} value={title} onChange={e => setTitle(e.target.value)} maxLength={240} />
      <Field label={t('bookSource')} value={url} onChange={e => setUrl(e.target.value)} type="url" dir="ltr" />
      <Button type="submit" variant="secondary" disabled={busy || title.trim().length < 2 || !url.startsWith('https://')}>{t('newBook')}</Button>
    </form>}
    {message && <Alert kind="error">{message}</Alert>}
  </section>;
}
