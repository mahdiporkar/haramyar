import {useEffect, useState} from 'react';

/** Minimal History-API router: the URL is the single source of truth for the shell and for mounted micro-apps. */
export function usePath(): string {
  const [path, setPath] = useState(() => location.pathname);
  useEffect(() => {
    const update = () => setPath(location.pathname);
    addEventListener('popstate', update);
    addEventListener('haramyar:navigate', update);
    return () => {
      removeEventListener('popstate', update);
      removeEventListener('haramyar:navigate', update);
    };
  }, []);
  return path;
}

export function navigate(path: string, options: {replace?: boolean} = {}): void {
  if (!path.startsWith('/') || path.startsWith('//')) return;
  if (path === location.pathname + location.search) return;
  if (options.replace) history.replaceState(null, '', path);
  else history.pushState(null, '', path);
  dispatchEvent(new Event('haramyar:navigate'));
  scrollTo({top: 0});
}
