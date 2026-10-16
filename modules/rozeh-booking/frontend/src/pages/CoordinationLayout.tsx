import type {ReactNode} from 'react';
import {Brand, Icon} from '@haramyar/design-system';
import {common, translator} from '@haramyar/i18n';
import {useEnv} from '../env.tsx';

/** Coordinator workspace frame (the prototype's provider dashboard layout). */
export function CoordinationLayout({active, children}: {active: 'overview' | 'providers'; children: ReactNode}) {
  const {t, navigate, locale} = useEnv();
  const c = translator(common, locale);
  return (
    <div className="provider-page">
      <aside>
        <Brand name={c('brandName')} tagline={c('brandTagline')} onClick={() => navigate('/')} />
        <nav aria-label={t('coordinationTitle')}>
          <button className={active === 'overview' ? 'active' : ''} onClick={() => navigate('/rozeh/coordination')}><Icon name="grid" /> {t('coordinationHello')}</button>
          <button className={active === 'providers' ? 'active' : ''} onClick={() => navigate('/rozeh/providers')}><Icon name="users" /> {t('providers')}</button>
        </nav>
        <button onClick={() => navigate('/account')}><Icon name="back" /> {c('back')}</button>
      </aside>
      <main>{children}</main>
    </div>
  );
}
