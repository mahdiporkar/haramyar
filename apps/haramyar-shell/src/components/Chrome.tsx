import type {ThemeMode} from '@haramyar/design-system';
import {Icon, type IconName} from '@haramyar/design-system';
import {LOCALE_LABELS, LOCALES, localizeDigits} from '@haramyar/i18n';
import {can} from '../hive.ts';
import {navigate} from '../router.ts';
import {useShell} from '../shell.tsx';

function ThemePreview() {
  const {t, theme, previewTheme} = useShell();
  const options: {key: ThemeMode; label: string; icon: IconName}[] = [
    {key: 'default', label: t('defaultTheme'), icon: 'home'}, {key: 'celebration', label: t('celebrationTheme'), icon: 'spark'}, {key: 'mourning', label: t('mourningTheme'), icon: 'mosque'},
  ];
  return (
    <div className="header-theme-switcher" role="group" aria-label={t('theme')}>
      {options.map(o => <button key={o.key} className={theme === o.key ? 'active' : ''} aria-pressed={theme === o.key} title={o.label} onClick={() => previewTheme(o.key)}><Icon name={o.icon} size={17} /><span>{o.label}</span></button>)}
    </div>
  );
}

function LanguageSwitcher() {
  const {locale, setLocale, t} = useShell();
  return (
    <div className="language-switcher" role="group" aria-label={t('language')}>
      {LOCALES.map(l => <button key={l} lang={l} className={locale === l ? 'active' : ''} aria-pressed={locale === l} title={LOCALE_LABELS[l].native} aria-label={LOCALE_LABELS[l].native} onClick={() => setLocale(l)} data-testid={`lang-${l}`}>{LOCALE_LABELS[l].short}</button>)}
    </div>
  );
}

export function Header({onSearch, onNotifications, unread}: {onSearch: () => void; onNotifications: () => void; unread: number}) {
  const {t, context, locale} = useShell();
  const initial = context.authenticated ? (context.identity.displayName.trim()[0] ?? '•').toUpperCase() : '';
  // Theme switching is an administrative capability; staff with occasion rights get a local preview here.
  const preview = can(context, 'haramyar-app.occasions', 'view');
  return (
    <header className="topbar">
      <button className="header-brand" aria-label={t('home')} onClick={() => navigate('/')}><img src="/assets/brand/haramyar-header-logo.svg" alt={t('brandName')} /><span>{t('brandTagline')}</span></button>
      {preview ? <ThemePreview /> : <span />}
      <LanguageSwitcher />
      <div className="header-actions">
        <button className="icon-btn" aria-label={t('search')} onClick={onSearch} data-testid="open-search"><Icon name="search" /></button>
        {context.authenticated && (
          <button className="icon-btn notify" aria-label={t('notifications')} onClick={onNotifications} data-testid="open-notifications">
            <Icon name="bell" />{unread > 0 && <b className="count">{localizeDigits(String(Math.min(unread, 99)), locale)}</b>}
          </button>
        )}
        <button className={`avatar-btn ${context.authenticated ? '' : 'guest'}`} aria-label={t('account')} onClick={() => navigate('/account')} data-testid="avatar">
          {context.authenticated ? initial : <Icon name="user" size={18} />}
        </button>
      </div>
    </header>
  );
}

const NAV: {path: string; key: 'home' | 'services' | 'activity' | 'account'; icon: IconName}[] = [
  {path: '/', key: 'home', icon: 'home'}, {path: '/services', key: 'services', icon: 'grid'}, {path: '/activity', key: 'activity', icon: 'activity'}, {path: '/account', key: 'account', icon: 'user'},
];

export function BottomNav({path}: {path: string}) {
  const {t} = useShell();
  return (
    <nav className="bottom-nav" aria-label={t('brandName')}>
      {NAV.map(item => <button key={item.path} className={path === item.path ? 'active' : ''} aria-current={path === item.path ? 'page' : undefined} onClick={() => navigate(item.path)} data-testid={`nav-${item.key}`}><Icon name={item.icon} /><span>{t(item.key)}</span></button>)}
    </nav>
  );
}
