import {useState, type ReactNode} from 'react';
import {Alert, Brand, Button, EmptyState, Field, Icon, Loading, SelectField, StatusBadge} from '@haramyar/design-system';
import {common, formatDateTime, translator} from '@haramyar/i18n';
import {api, type Status} from '../api.ts';
import {useEnv, useLoad} from '../env.tsx';
import type {MessageKey} from '../messages.ts';

export function EditorialLayout({children}: {children: ReactNode}) {
  const {t, navigate, locale} = useEnv();
  const c = translator(common, locale);
  return (
    <div className="admin-page">
      <aside>
        <Brand name={c('brandName')} tagline={c('brandTagline')} onClick={() => navigate('/')} />
        <nav><button className="active" onClick={() => navigate('/martyrs/editorial')}><Icon name="document" /> {t('editorial')}</button>
          <button onClick={() => navigate('/martyrs')}><Icon name="book" /> {t('title')}</button></nav>
        <button onClick={() => navigate('/account')}><Icon name="back" /> {c('back')}</button>
      </aside>
      <main>{children}</main>
    </div>
  );
}

const FILTERS: {key: MessageKey; statuses: Status[]}[] = [
  {key: 'all', statuses: []}, {key: 'status_DRAFT', statuses: ['DRAFT']}, {key: 'status_IN_REVIEW', statuses: ['IN_REVIEW']},
  {key: 'status_PUBLISHED', statuses: ['PUBLISHED']}, {key: 'status_ARCHIVED', statuses: ['ARCHIVED']},
];

export function EditorialList() {
  const {t, navigate, locale, errorText, route, can} = useEnv();
  const [filter, setFilter] = useState(0);
  const [query, setQuery] = useState('');
  const list = useLoad(() => api.editorialList(FILTERS[filter]!.statuses, query), [filter, query]);
  const [creating, setCreating] = useState(false);
  const [slug, setSlug] = useState('');
  const [fullName, setFullName] = useState('');
  const [contentLocale, setContentLocale] = useState<string>(locale);
  const [failure, setFailure] = useState<string | null>(null);

  const create = async () => {
    setFailure(null);
    try {
      const created = await api.create(slug.trim(), {fullName: fullName.trim(), contentLocale});
      navigate(`/martyrs/editorial/${created.id}`);
    } catch (error) {
      setFailure(errorText(error, route));
    }
  };

  return (
    <EditorialLayout>
      <div className="admin-heading">
        <div><small>{t('title')}</small><h1>{t('editorial')}</h1><p>{t('editorialHint')}</p></div>
        {can('martyrs.editorial', 'edit') && <Button icon="plus" onClick={() => setCreating(v => !v)} testId="new-profile">{t('newProfile')}</Button>}
      </div>
      {creating && (
        <form className="panel" onSubmit={e => { e.preventDefault(); void create(); }} style={{marginBottom: 20}}>
          <h2>{t('newProfile')}</h2>
          <Field label={t('fullName')} value={fullName} onChange={e => setFullName(e.target.value)} maxLength={160} name="fullName" />
          <Field label={t('slug')} hint={t('slugHint')} value={slug} onChange={e => setSlug(e.target.value.toLowerCase())} dir="ltr" maxLength={120} name="slug" pattern="[a-z0-9]+(-[a-z0-9]+)*" />
          <SelectField label={t('contentLocale')} value={contentLocale} onChange={e => setContentLocale(e.target.value)} options={[{value: 'fa', label: 'فارسی'}, {value: 'ar', label: 'العربية'}, {value: 'en', label: 'English'}]} />
          {failure && <Alert kind="error">{failure}</Alert>}
          <Button type="submit" disabled={fullName.trim().length < 2 || slug.trim().length < 3} testId="create-profile">{t('create')}</Button>
        </form>
      )}
      <div className="workspace-toolbar">
        <div className="filter-chips">{FILTERS.map((f, i) => <button key={f.key} className={filter === i ? 'selected' : ''} onClick={() => setFilter(i)}>{t(f.key)}</button>)}</div>
        <div className="real-search" style={{maxWidth: 280, minHeight: 44}}><Icon name="search" size={18} /><input value={query} onChange={e => setQuery(e.target.value)} placeholder={t('searchPlaceholder')} /></div>
      </div>
      {list.loading && !list.data && <Loading label={t('loading')} />}
      {!!list.error && <Alert kind="error">{errorText(list.error, route)}</Alert>}
      {list.data && list.data.items.length === 0 && <EmptyState icon="document" title={t('emptyArchive')} />}
      {list.data && list.data.items.length > 0 && (
        <section className="panel">
          <table className="list-table" data-testid="editorial-table">
            <thead><tr><th>{t('fullName')}</th><th>{t('status')}</th><th>{t('updated')}</th></tr></thead>
            <tbody>{list.data.items.map(p => (
              <tr key={p.id} className="clickable" tabIndex={0} data-profile-id={p.id} onClick={() => navigate(`/martyrs/editorial/${p.id}`)} onKeyDown={e => { if (e.key === 'Enter') navigate(`/martyrs/editorial/${p.id}`); }}>
                <td><b>{p.fullName}</b><small className="ltr">{p.slug}</small></td>
                <td><StatusBadge status={p.status} label={t(`status_${p.status}` as MessageKey)} /></td>
                <td>{formatDateTime(p.updatedAt, locale)}</td>
              </tr>
            ))}</tbody>
          </table>
        </section>
      )}
    </EditorialLayout>
  );
}
