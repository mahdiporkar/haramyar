import {useState} from 'react';
import {Alert, Button, EmptyState, Field, Loading, StatusBadge} from '@haramyar/design-system';
import {formatDate} from '@haramyar/i18n';
import {api} from '../api.ts';
import {useEnv, useLoad} from '../env.tsx';
import {CoordinationLayout} from './CoordinationLayout.tsx';

/** Coordinator-maintained registry of providers that can be assigned to accepted bookings. */
export function Providers() {
  const {t, locale, errorText, route} = useEnv();
  const list = useLoad(() => api.providers(true), []);
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setFailure(null);
    try {
      await action();
      list.reload();
    } catch (error) {
      setFailure(errorText(error, route));
      list.reload();
    } finally {
      setBusy(false);
    }
  };

  return (
    <CoordinationLayout active="providers">
      <div className="provider-head"><div><small>{t('coordinationTitle')}</small><h1>{t('providers')}</h1></div></div>
      <section className="provider-panel" style={{padding: 22}}>
        <form className="workspace-toolbar" onSubmit={e => { e.preventDefault(); if (name.trim().length >= 2) void run(() => api.registerProvider(name.trim()).then(() => setName(''))); }}>
          <div style={{flex: 1, minWidth: 220}}><Field label={t('newProvider')} placeholder={t('providerName')} value={name} onChange={e => setName(e.target.value)} maxLength={120} /></div>
          <Button type="submit" disabled={busy || name.trim().length < 2} icon="plus">{t('register')}</Button>
        </form>
        {failure && <Alert kind="error">{failure}</Alert>}
        {list.loading && !list.data && <Loading label={t('loading')} />}
        {list.data && list.data.length === 0 && <EmptyState icon="users" title={t('providers')} />}
        {list.data && list.data.length > 0 && (
          <table className="list-table">
            <thead><tr><th>{t('providerName')}</th><th>{t('status')}</th><th /></tr></thead>
            <tbody>
              {list.data.map(p => (
                <tr key={p.id}>
                  <td><b>{p.displayName}</b><small>{formatDate(p.registeredAt, locale, undefined, 'medium')}</small></td>
                  <td><StatusBadge status={p.active ? 'active' : 'archived'} label={p.active ? t('active') : t('inactive')} /></td>
                  <td><button className="btn btn-ghost" disabled={busy} onClick={() => run(() => api.updateProvider(p.id, p.version, {active: !p.active}))}>{p.active ? t('deactivate') : t('activate')}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </CoordinationLayout>
  );
}
