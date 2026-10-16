import {useEffect, useState} from 'react';
import {Icon} from '@haramyar/design-system';
import {formatDateTime, localizeDigits, type CommonKey} from '@haramyar/i18n';
import {http} from '../hive.ts';
import {navigate} from '../router.ts';
import {useShell} from '../shell.tsx';

interface PublicSummary {slug: string; fullName: string; shortDescription?: string}
interface Notice {id: string; kind: string; subjectId: string; reference?: string; occurredAt: string; readAt?: string}

function Sheet({title, onClose, children}: {title: string; onClose: () => void; children: React.ReactNode}) {
  const {t} = useShell();
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    addEventListener('keydown', onKey);
    return () => removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="overlay" role="dialog" aria-modal="true" aria-label={title}>
      <button className="overlay-bg" onClick={onClose} aria-label={t('close')} />
      <div className="search-sheet">
        <div className="sheet-handle" />
        <div className="sheet-head"><h2>{title}</h2><button className="icon-btn" onClick={onClose} aria-label={t('close')}><Icon name="close" /></button></div>
        {children}
      </div>
    </div>
  );
}

/** Searches public content only: published martyr profiles (public API) and service shortcuts. */
export function SearchOverlay({onClose}: {onClose: () => void}) {
  const {t} = useShell();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<PublicSummary[]>([]);
  useEffect(() => {
    if (query.trim().length < 2) { setResults([]); return; }
    const controller = new AbortController();
    const timer = setTimeout(() => {
      http.get<{items: PublicSummary[]}>(`/api/routes/martyrs/public/profiles?q=${encodeURIComponent(query.trim())}&size=5`, {signal: controller.signal})
        .then(page => setResults(page.items)).catch(() => setResults([]));
    }, 250);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [query]);
  const go = (path: string) => { onClose(); navigate(path); };
  return (
    <Sheet title={t('searchTitle')} onClose={onClose}>
      <div className="real-search"><Icon name="search" /><input autoFocus value={query} onChange={e => setQuery(e.target.value)} placeholder={t('searchPlaceholder')} maxLength={100} data-testid="search-input" /></div>
      <small className="search-caption">{query ? t('searchResults') : t('searchSuggested')}</small>
      {results.map(r => (
        <button key={r.slug} className="search-result" onClick={() => go(`/martyrs/profiles/${r.slug}`)}><span><Icon name="book" /></span><div><b>{r.fullName}</b><small>{r.shortDescription ?? t('martyrs')}</small></div><Icon name="chevron" /></button>
      ))}
      {query.trim().length >= 2 && results.length === 0 && <p className="privacy-search">{t('noResults')}</p>}
      <button className="search-result" onClick={() => go('/martyrs/directory')}><span><Icon name="book" /></span><div><b>{t('lastingStories')}</b><small>{t('martyrs')}</small></div><Icon name="chevron" /></button>
      <button className="search-result" onClick={() => go('/rozeh')}><span><Icon name="calendar" /></span><div><b>{t('booking')}</b><small>{t('activeService')}</small></div><Icon name="chevron" /></button>
      <div className="privacy-search"><Icon name="lock" /> {t('searchPrivacy')}</div>
    </Sheet>
  );
}

export function useNotifications(enabled: boolean) {
  const [state, setState] = useState<{items: Notice[]; unread: number}>({items: [], unread: 0});
  const [tick, setTick] = useState(0);
  useEffect(() => {
    if (!enabled) { setState({items: [], unread: 0}); return; }
    let active = true;
    const load = () => http.get<{items: Notice[]; unread: number}>('/api/routes/haramyar/notifications').then(value => { if (active) setState(value); }).catch(() => undefined);
    void load();
    const timer = setInterval(load, 30_000);
    return () => { active = false; clearInterval(timer); };
  }, [enabled, tick]);
  return {...state, reload: () => setTick(n => n + 1)};
}

export function NotificationsSheet({items, onClose, onRead}: {items: Notice[]; onClose: () => void; onRead: () => void}) {
  const {t, locale} = useShell();
  const open = async (notice: Notice) => {
    if (!notice.readAt) await http.post(`/api/routes/haramyar/notifications/${encodeURIComponent(notice.id)}/read`).catch(() => undefined);
    onRead();
    onClose();
    if (notice.kind.startsWith('BOOKING_')) navigate(`/rozeh/requests/${notice.subjectId}`);
  };
  return (
    <Sheet title={t('notifications')} onClose={onClose}>
      {items.length === 0 && <p className="privacy-search">{t('noNotifications')}</p>}
      <div className="notice-list" data-testid="notifications">
        {items.map(n => (
          <button key={n.id} className={`notice ${n.readAt ? '' : 'unread'}`} onClick={() => open(n)} data-kind={n.kind} data-subject-id={n.subjectId}>
            <span><Icon name={n.kind === 'BOOKING_CONFIRMED' || n.kind === 'BOOKING_ACCEPTED' ? 'check' : n.kind === 'BOOKING_REJECTED' || n.kind === 'BOOKING_CANCELLED' ? 'close' : 'bell'} /></span>
            <div><b>{t(`notice_${n.kind}` as CommonKey)}</b><small>{n.reference && <><span className="ltr">{localizeDigits(n.reference, locale)}</span> · </>}{formatDateTime(n.occurredAt, locale)}</small></div>
          </button>
        ))}
      </div>
    </Sheet>
  );
}
