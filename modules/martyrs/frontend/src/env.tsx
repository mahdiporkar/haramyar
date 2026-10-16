import {createContext, useCallback, useContext, useEffect, useState} from 'react';
import type {HiveMountContext} from '@hive-platform/contracts';
import {defaultZone, isLocale, translator, type Direction, type Locale} from '@haramyar/i18n';
import {useShellTheme, type ThemeMode} from '@haramyar/design-system';
import {errorCode, isUnauthenticated, login} from './api.ts';
import {messages, type MessageKey} from './messages.ts';

export interface ModuleEnv {
  locale: Locale;
  direction: Direction;
  theme: ThemeMode;
  t: (key: MessageKey, params?: Record<string, string | number>) => string;
  route: string;
  params: Readonly<Record<string, string>>;
  authenticated: boolean;
  /** UI hint only; the BFF and the backend enforce every operation. */
  can: (resource: string, action: string) => boolean;
  navigate: (path: string) => void;
  zone: string;
  /** Localized text for an API failure; sends the user to sign in when the session is missing. */
  errorText: (error: unknown, returnUrl?: string) => string;
}

const Env = createContext<ModuleEnv | null>(null);

export function useEnv(): ModuleEnv {
  const env = useContext(Env);
  if (!env) throw new Error('useEnv outside EnvProvider');
  return env;
}

export function EnvProvider({context, children}: {context: HiveMountContext; children: React.ReactNode}) {
  const theme = useShellTheme();
  const locale: Locale = isLocale(context.locale) ? context.locale : 'fa';
  const t = translator(messages, locale);
  const errorText = (error: unknown, returnUrl?: string) => {
    if (isUnauthenticated(error) && returnUrl) {
      login(returnUrl);
      return t('e_AUTHENTICATION_REQUIRED');
    }
    const key = `e_${errorCode(error)}` as MessageKey;
    return key in messages.fa ? t(key) : t('e_GENERIC');
  };
  const value: ModuleEnv = {
    locale, direction: context.direction, theme, t, route: context.route, params: context.params,
    authenticated: context.context.authenticated,
    can: (resource, action) => context.permissions.can(resource, action),
    navigate: path => context.navigate(path), zone: defaultZone(locale), errorText,
  };
  return <Env.Provider value={value}>{children}</Env.Provider>;
}

/** Loads data with explicit loading/error states and a reload function. */
export function useLoad<T>(load: () => Promise<T>, deps: unknown[]): {data: T | null; error: unknown; loading: boolean; reload: () => void; setData: (value: T) => void} {
  const [state, setState] = useState<{data: T | null; error: unknown; loading: boolean}>({data: null, error: null, loading: true});
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let active = true;
    setState(previous => ({...previous, loading: true, error: null}));
    load().then(data => { if (active) setState({data, error: null, loading: false}); }, error => { if (active) setState({data: null, error, loading: false}); });
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);
  const reload = useCallback(() => setTick(n => n + 1), []);
  const setData = useCallback((data: T) => setState({data, error: null, loading: false}), []);
  return {...state, reload, setData};
}
