import {createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode} from 'react';
import type {AnyHiveContext} from '@hive-platform/contracts';
import type {ThemeMode} from '@haramyar/design-system';
import {common, directionOf, storedLocale, storeLocale, translator, type CommonKey, type Direction, type Locale} from '@haramyar/i18n';
import {http, loadContext} from './hive.ts';

export interface Occasion {key: string; titles: Partial<Record<Locale, string>>; subtitles: Partial<Record<Locale, string>>; endsAt: string}

interface ShellState {
  context: AnyHiveContext;
  locale: Locale;
  direction: Direction;
  theme: ThemeMode;
  occasion: Occasion | null;
  t: (key: CommonKey, params?: Record<string, string | number>) => string;
  setLocale: (locale: Locale) => void;
  /** Staff-only local preview; it never changes the configured theme or any business state. */
  previewTheme: (theme: ThemeMode | null) => void;
  refresh: () => Promise<void>;
}

const Shell = createContext<ShellState | null>(null);

export function useShell(): ShellState {
  const value = useContext(Shell);
  if (!value) throw new Error('useShell outside ShellProvider');
  return value;
}

/** Keycloak renders its login and registration pages in the language chosen here (same-origin realm path). */
function shareLocaleWithIdentityProvider(locale: Locale) {
  const secure = location.protocol === 'https:' ? '; Secure' : '';
  document.cookie = `KEYCLOAK_LOCALE=${locale}; path=/realms/haramyar/; SameSite=Lax${secure}`;
}

export function ShellProvider({initial, children}: {initial: AnyHiveContext; children: ReactNode}) {
  const [context, setContext] = useState(initial);
  const [locale, setLocaleState] = useState<Locale>(storedLocale);
  const [configured, setConfigured] = useState<{theme: ThemeMode; occasion: Occasion | null}>({theme: 'default', occasion: null});
  const [preview, setPreview] = useState<ThemeMode | null>(null);
  const direction = directionOf(locale);
  const theme = preview ?? configured.theme;

  useEffect(() => {
    document.documentElement.lang = locale;
    document.documentElement.dir = direction;
    shareLocaleWithIdentityProvider(locale);
  }, [locale, direction]);
  useEffect(() => { document.documentElement.dataset.theme = theme; }, [theme]);

  useEffect(() => {
    http.get<{theme: string; occasion?: Occasion}>('/api/routes/haramyar/public/theme')
      .then(value => setConfigured({theme: value.theme === 'celebration' || value.theme === 'mourning' ? value.theme : 'default', occasion: value.occasion ?? null}))
      .catch(() => setConfigured({theme: 'default', occasion: null}));
  }, []);

  const refresh = useCallback(async () => {
    const next = await loadContext();
    setContext(previous => (JSON.stringify(previous) === JSON.stringify(next) ? previous : next));
  }, []);

  // Grants, activations and rollbacks reach open sessions without a new login (as Hive's default shell does).
  useEffect(() => {
    const onFocus = () => void refresh().catch(() => undefined);
    addEventListener('focus', onFocus);
    const timer = setInterval(onFocus, 60_000);
    return () => { removeEventListener('focus', onFocus); clearInterval(timer); };
  }, [refresh]);

  const value = useMemo<ShellState>(() => ({
    context, locale, direction, theme, occasion: configured.occasion, t: translator(common, locale),
    setLocale: next => { storeLocale(next); setLocaleState(next); },
    previewTheme: setPreview, refresh,
  }), [context, locale, direction, theme, configured.occasion, refresh]);
  return <Shell.Provider value={value}>{children}</Shell.Provider>;
}
