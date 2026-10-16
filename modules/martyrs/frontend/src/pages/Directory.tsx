import {useEffect, useState} from 'react';
import {Alert, Button, EmptyState, Icon, Loading, ModuleBar} from '@haramyar/design-system';
import {api, publicMediaUrl} from '../api.ts';
import {useEnv, useLoad} from '../env.tsx';
import {count, yearLabel} from '../labels.ts';

/** Search over published profiles only (the backend never returns drafts). */
export function Directory() {
  const {t, navigate, locale, errorText} = useEnv();
  const [input, setInput] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  useEffect(() => {
    const timer = setTimeout(() => { setQuery(input); setPage(0); }, 300);
    return () => clearTimeout(timer);
  }, [input]);
  const results = useLoad(() => api.search(query, page, 20), [query, page]);
  const pages = results.data ? Math.max(1, Math.ceil(results.data.total / results.data.size)) : 1;
  return (
    <div className="module-page">
      <ModuleBar title={t('directory')} onBack={() => navigate('/martyrs')} backLabel={t('back')} />
      <div className="module-body">
        <div className="real-search">
          <Icon name="search" />
          <input value={input} onChange={e => setInput(e.target.value)} placeholder={t('searchPlaceholder')} aria-label={t('searchPlaceholder')} maxLength={100} data-testid="directory-search" />
          {input && <button onClick={() => setInput('')} aria-label="×"><Icon name="close" size={18} /></button>}
        </div>
        <div className="result-head">
          <h1>{query ? t('resultsFor', {q: query}) : t('latest')}</h1>
          {results.data && <small>{t('resultCount', {n: count(locale, results.data.total)})}</small>}
        </div>
        {results.loading && !results.data && <Loading label={t('loading')} />}
        {!!results.error && <Alert kind="error">{errorText(results.error)}</Alert>}
        {results.data && results.data.items.length === 0 && <EmptyState icon="search" title={query ? t('noResults') : t('emptyArchive')} hint={t('emptyArchiveHint')} />}
        <div className="profile-list" data-testid="directory-results">
          {results.data?.items.map(p => (
            <button key={p.slug} data-slug={p.slug} onClick={() => navigate(`/martyrs/profiles/${p.slug}`)}>
              <span className="mini-portrait">{p.portraitMediaId ? <img src={publicMediaUrl(p.portraitMediaId)} alt="" loading="lazy" /> : <Icon name="user" />}</span>
              <div>
                <small>{[yearLabel(t, locale, p.birthYear, p.birthCalendar), yearLabel(t, locale, p.martyrdomYear, p.martyrdomCalendar)].filter(Boolean).join(' – ') || t('profile')}</small>
                <b>{p.honorific ? `${p.honorific} ` : ''}{p.fullName}</b>
                <p>{p.shortDescription}</p>
              </div>
              <Icon name="chevron" />
            </button>
          ))}
        </div>
        {results.data && results.data.total > results.data.size && (
          <div className="pager">
            <Button variant="ghost" disabled={page === 0} onClick={() => setPage(p => p - 1)}>‹</Button>
            <span>{count(locale, page + 1)} / {count(locale, pages)}</span>
            <Button variant="ghost" disabled={page + 1 >= pages} onClick={() => setPage(p => p + 1)}>›</Button>
          </div>
        )}
        <div className="safety-note"><Icon name="check" /><p><b>{t('accuracyTitle')}</b>{t('accuracyHint')}</p></div>
      </div>
    </div>
  );
}
