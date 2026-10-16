import {useEffect, useState} from 'react';
import {Button, Icon, SectionTitle} from '@haramyar/design-system';
import {useShell} from '../shell.tsx';
import {ServiceCard} from './Home.tsx';

interface InstallPrompt extends Event {prompt(): Promise<void>}

/** Services catalogue. Today's services are listed explicitly; future modules appear here as they are registered. */
export function Services() {
  const {t} = useShell();
  const [install, setInstall] = useState<InstallPrompt | null>(null);
  useEffect(() => {
    const capture = (event: Event) => { event.preventDefault(); setInstall(event as InstallPrompt); };
    addEventListener('beforeinstallprompt', capture);
    return () => removeEventListener('beforeinstallprompt', capture);
  }, []);
  return (
    <div className="page compact-page" id="content">
      <div className="page-heading"><span>{t('services')}</span><h1>{t('haramyarServices')}</h1><p>{t('brandTagline')}</p></div>
      <div className="services-grid services-page"><ServiceCard type="martyrs" /><ServiceCard type="booking" /></div>
      <SectionTitle title={t('moreSoon')} />
      <div className="future-service"><span><Icon name="spark" /></span><div><b>{t('moreSoon')}</b><small>{t('moreSoonHint')}</small></div></div>
      <div className="install-card">
        <span className="app-icon"><img src="/assets/brand/haramyar-app-icon-192.png" alt="" /></span>
        <div><b>{t('installTitle')}</b><p>{t('installHint')}</p></div>
        {install && <Button variant="secondary" onClick={() => { void install.prompt(); setInstall(null); }}>{t('install')}</Button>}
      </div>
    </div>
  );
}
