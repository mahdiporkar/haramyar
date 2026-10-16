import {useState} from 'react';
import {Alert, EmptyState, Icon, Loading, StatusBadge} from '@haramyar/design-system';
import {formatNumber} from '@haramyar/i18n';
import {api, type BookingStatus} from '../api.ts';
import {useEnv, useLoad} from '../env.tsx';
import {codeLabel, statusLabel, when} from '../labels.ts';
import {CoordinationLayout} from './CoordinationLayout.tsx';

const FILTERS: {key: 'all' | 'inProgress' | 'confirmedFilter' | 'finished'; statuses: BookingStatus[]}[] = [
  {key: 'inProgress', statuses: ['REQUESTED', 'ACCEPTED']},
  {key: 'confirmedFilter', statuses: ['CONFIRMED']},
  {key: 'finished', statuses: ['REJECTED', 'CANCELLED', 'COMPLETED']},
  {key: 'all', statuses: []},
];

/** Coordinator dashboard backed by real data: counts and the request queue, filtered by status and city. */
export function Coordination() {
  const {t, navigate, locale, errorText, route} = useEnv();
  const [filter, setFilter] = useState(0);
  const [city, setCity] = useState('');
  const [page, setPage] = useState(0);
  const list = useLoad(() => api.coordination({status: FILTERS[filter]!.statuses, city: city.trim() || undefined, page}), [filter, city, page]);
  const metrics = useLoad(async () => {
    const [requested, confirmed, providers] = await Promise.all([api.coordination({status: ['REQUESTED']}), api.coordination({status: ['CONFIRMED']}), api.providers(false)]);
    return {requested: requested.total, confirmed: confirmed.total, providers: providers.length};
  }, []);
  const pages = list.data ? Math.max(1, Math.ceil(list.data.total / list.data.size)) : 1;
  return (
    <CoordinationLayout active="overview">
      <div className="provider-head"><div><small>{t('coordinationTitle')}</small><h1>{t('coordinationHello')}</h1></div></div>
      {!!metrics.error && <Alert kind="error">{errorText(metrics.error, route)}</Alert>}
      <div className="metric-grid" data-testid="metrics">
        <div><small>{t('newRequests')}</small><b>{metrics.data ? formatNumber(metrics.data.requested, locale) : '…'}</b><span>{t('needsReview')}</span></div>
        <div><small>{t('confirmedCount')}</small><b>{metrics.data ? formatNumber(metrics.data.confirmed, locale) : '…'}</b><span>{t('status_CONFIRMED')}</span></div>
        <div><small>{t('providersCount')}</small><b>{metrics.data ? formatNumber(metrics.data.providers, locale) : '…'}</b><span>{t('active')}</span></div>
      </div>
      <section className="provider-panel">
        <div className="workspace-toolbar">
          <div className="filter-chips" role="tablist">
            {FILTERS.map((f, i) => <button key={f.key} role="tab" aria-selected={filter === i} className={filter === i ? 'selected' : ''} onClick={() => { setFilter(i); setPage(0); }}>{t(f.key)}</button>)}
          </div>
          <div className="real-search" style={{maxWidth: 260, minHeight: 44}}>
            <Icon name="filter" size={18} />
            <input value={city} onChange={e => { setCity(e.target.value); setPage(0); }} placeholder={t('filterCity')} aria-label={t('filterCity')} />
          </div>
        </div>
        {list.loading && !list.data && <Loading label={t('loading')} />}
        {!!list.error && <Alert kind="error">{errorText(list.error, route)}</Alert>}
        {list.data && list.data.items.length === 0 && <EmptyState icon="calendar" title={t('noRequests')} />}
        {list.data && list.data.items.length > 0 && (
          <table className="list-table" data-testid="coordination-table">
            <thead><tr><th>{t('host')}</th><th>{t('when')}</th><th>{t('place')}</th><th>{t('status')}</th></tr></thead>
            <tbody>
              {list.data.items.map(item => {
                const time = when(locale, item.startsAt, item.endsAt, item.timeZone);
                return (
                  <tr key={item.id} className="clickable" data-booking-id={item.id} tabIndex={0} onClick={() => navigate(`/rozeh/coordination/${item.id}`)}
                    onKeyDown={e => { if (e.key === 'Enter') navigate(`/rozeh/coordination/${item.id}`); }}>
                    <td><b>{item.hostName}</b><small>{codeLabel(t, 'audience', item.audience)} · <span className="ltr">{item.trackingCode}</span></small></td>
                    <td><b>{time.date}</b><small className="ltr">{time.time}</small></td>
                    <td>{[item.city, item.area].filter(Boolean).join('، ')}{item.providerName && <small>{item.providerName}</small>}</td>
                    <td><StatusBadge status={item.status} label={statusLabel(t, item.status)} /></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
        {list.data && list.data.total > list.data.size && (
          <div className="pager">
            <button className="btn btn-ghost" disabled={page === 0} onClick={() => setPage(p => p - 1)}>{t('previous')}</button>
            <span>{t('pageOf', {page: formatNumber(page + 1, locale), pages: formatNumber(pages, locale)})}</span>
            <button className="btn btn-ghost" disabled={page + 1 >= pages} onClick={() => setPage(p => p + 1)}>{t('next')}</button>
          </div>
        )}
      </section>
    </CoordinationLayout>
  );
}
