import {useEffect, useState} from 'react';
import type {AnyHiveContext} from '@hive-platform/contracts';
import {HyRoot, Loading} from '@haramyar/design-system';
import {common, storedLocale, translator} from '@haramyar/i18n';
import {BottomNav, Header} from './components/Chrome.tsx';
import {ModuleHost} from './components/ModuleHost.tsx';
import {NotificationsSheet, SearchOverlay, useNotifications} from './components/Sheets.tsx';
import SplashScreen from './components/SplashScreen.tsx';
import {can, loadContext} from './hive.ts';
import {Account} from './pages/Account.tsx';
import {Activity} from './pages/Activity.tsx';
import {Home} from './pages/Home.tsx';
import {OccasionAdmin} from './pages/OccasionAdmin.tsx';
import {Services} from './pages/Services.tsx';
import {usePath} from './router.ts';
import {ShellProvider, useShell} from './shell.tsx';

const SHELL_PAGES: Record<string, () => React.JSX.Element> = {'/': Home, '/services': Services, '/activity': Activity, '/account': Account};
const SPLASH_KEY = 'haramyar.splash-shown';

function Frame() {
  const {theme, locale, direction, context, t} = useShell();
  const path = usePath();
  const [search, setSearch] = useState(false);
  const [notices, setNotices] = useState(false);
  const notifications = useNotifications(context.authenticated && can(context, 'haramyar-app.notifications', 'view'));
  const Page = SHELL_PAGES[path];
  const admin = path === '/admin/occasions';
  const shell = !!Page;
  return (
    <HyRoot theme={theme} locale={locale} direction={direction} className={`app ${shell ? 'has-shell' : ''}`}>
      <a className="sr-only" href="#content">{t('skipToContent')}</a>
      {shell && <Header onSearch={() => setSearch(true)} onNotifications={() => { notifications.reload(); setNotices(true); }} unread={notifications.unread} />}
      {Page ? <Page /> : admin ? <OccasionAdmin /> : <ModuleHost path={path} />}
      {shell && <BottomNav path={path} />}
      {search && <SearchOverlay onClose={() => setSearch(false)} />}
      {notices && <NotificationsSheet items={notifications.items} onClose={() => setNotices(false)} onRead={notifications.reload} />}
    </HyRoot>
  );
}

export default function App() {
  const [context, setContext] = useState<AnyHiveContext | null>(null);
  const [settingUp, setSettingUp] = useState(false);
  const [failure, setFailure] = useState(false);
  const [onboardingFailed, setOnboardingFailed] = useState<AnyHiveContext | null>(null);
  const [splash, setSplash] = useState(() => {
    try { return sessionStorage.getItem(SPLASH_KEY) !== '1'; } catch { return true; }
  });
  const t = translator(common, storedLocale());
  // One attempt per page load or explicit Retry; a failed onboarding is shown, never retried in a loop.
  const load = () => {
    setFailure(false);
    setOnboardingFailed(null);
    loadContext(() => setSettingUp(true))
      .then(({context: loaded, onboarding}) => (onboarding === 'failed' ? setOnboardingFailed(loaded) : setContext(loaded)))
      .catch(() => setFailure(true));
  };
  useEffect(load, []);
  const finishSplash = () => {
    try { sessionStorage.setItem(SPLASH_KEY, '1'); } catch { /* storage unavailable */ }
    setSplash(false);
  };
  return (
    <>
      {context ? <ShellProvider initial={context}><Frame /></ShellProvider>
        : <div className="hy hy-tokens" dir={storedLocale() === 'en' ? 'ltr' : 'rtl'}>{onboardingFailed
          ? <div className="loading" role="alert" data-testid="onboarding-failed"><p>{t('onboardingFailed')}</p>
              <button className="btn btn-primary" onClick={load} data-testid="onboarding-retry">{t('retry')}</button>
              <button className="btn btn-ghost" onClick={() => setContext(onboardingFailed)}>{t('continueWithoutSetup')}</button></div>
          : failure
          ? <div className="loading"><p>{t('error')}</p><button className="btn btn-primary" onClick={() => location.reload()}>{t('retry')}</button></div>
          : <Loading label={settingUp ? t('settingUp') : t('loading')} />}</div>}
      {splash && <div className="hy hy-tokens"><SplashScreen onComplete={finishSplash} label={t('splashLabel')} title={t('brandName')} tagline={t('brandTagline')} /></div>}
    </>
  );
}
