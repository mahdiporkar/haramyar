import {useState} from 'react';
import {Alert, Button, EmptyState, Icon, Loading, ModuleBar, StatusBadge} from '@haramyar/design-system';
import {api, type BookingStatus} from '../api.ts';
import {useEnv, useLoad} from '../env.tsx';
import {codeLabel, statusLabel, when} from '../labels.ts';

const FILTERS: {key: 'all' | 'inProgress' | 'confirmedFilter' | 'finished'; statuses: BookingStatus[]}[] = [
  {key: 'all', statuses: []},
  {key: 'inProgress', statuses: ['REQUESTED', 'ACCEPTED']},
  {key: 'confirmedFilter', statuses: ['CONFIRMED']},
  {key: 'finished', statuses: ['REJECTED', 'CANCELLED', 'COMPLETED']},
];

/** The signed-in host's own requests (also embedded by the shell in "My activity"). */
export function MyRequests() {
  const {t, navigate, locale, errorText} = useEnv();
  const [filter, setFilter] = useState(0);
  const list = useLoad(() => api.mine(FILTERS[filter]!.statuses), [filter]);
  return (
    <div className="module-page">
      <ModuleBar title={t('myRequests')} onBack={() => navigate('/rozeh')} backLabel={t('back')} />
      <div className="module-body compact-page">
        <div className="page-heading"><span>{t('title')}</span><h1>{t('myRequests')}</h1><p>{t('myRequestsHint')}</p></div>
        <div className="filter-chips" role="tablist">
          {FILTERS.map((f, i) => <button key={f.key} role="tab" aria-selected={filter === i} className={filter === i ? 'selected' : ''} onClick={() => setFilter(i)}>{t(f.key)}</button>)}
        </div>
        {list.loading && <Loading label={t('loading')} />}
        {!!list.error && <Alert kind="error">{errorText(list.error, '/rozeh/requests')} <button className="btn btn-ghost" onClick={list.reload}>{t('retry')}</button></Alert>}
        {list.data && list.data.items.length === 0 && (
          <EmptyState icon="mosque" title={t('noRequests')} hint={t('noRequestsHint')} action={<Button onClick={() => navigate('/rozeh/request')} icon="arrow">{t('startRequest')}</Button>} />
        )}
        {list.data?.items.map(item => {
          const time = when(locale, item.startsAt, item.endsAt, item.timeZone);
          return (
            <button key={item.id} className="request-card" data-testid="request-card" data-booking-id={item.id} onClick={() => navigate(`/rozeh/requests/${item.id}`)}>
              <div>
                <StatusBadge status={item.status} label={statusLabel(t, item.status)} />
                <small>{codeLabel(t, 'category', item.category)} · {codeLabel(t, 'audience', item.audience)}</small>
                <b>{time.date} · {time.time}</b>
                <p><Icon name="map" size={16} /> {[item.city, item.area].filter(Boolean).join('، ')}</p>
              </div>
              <Icon name="chevron" />
            </button>
          );
        })}
        <div className="activity-help"><Icon name="bell" /><div><b>{t('statusAlerts')}</b><p>{t('statusAlertsHint')}</p></div></div>
      </div>
    </div>
  );
}
