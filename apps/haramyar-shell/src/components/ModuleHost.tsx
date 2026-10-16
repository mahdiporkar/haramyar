import {useEffect, useRef, useState} from 'react';
import type {AnyHiveContext, RuntimeModule, RuntimeRoute} from '@hive-platform/contracts';
import {routeAccess, scopedPermissions} from '@hive-platform/authorization';
import {matchRoute, routeSpecificity} from '@hive-platform/core';
import type {MountedInstance, MountContextInput} from '@hive-platform/mfe-runtime';
import {Button, Icon, Loading} from '@haramyar/design-system';
import {APPLICATION, auth, events, runtime} from '../hive.ts';
import {navigate} from '../router.ts';
import {useShell} from '../shell.tsx';

export interface Resolution {module: RuntimeModule; route: RuntimeRoute; params: Record<string, string>}

/** Finds the most specific registered route for a path among the modules of the runtime context. */
export function resolve(context: AnyHiveContext, path: string): Resolution | null {
  let best: (Resolution & {score: number}) | null = null;
  for (const module of context.modules) {
    if (module.applicationKey !== APPLICATION) continue;
    for (const route of module.routes) {
      const params = matchRoute(route.path, path);
      if (params && (!best || routeSpecificity(route.path) > best.score)) best = {module, route, params, score: routeSpecificity(route.path)};
    }
  }
  return best;
}

type Status = 'LOADING' | 'MOUNTED' | 'LOGIN_REQUIRED' | 'DENIED' | 'NOT_FOUND' | 'ERROR';

/**
 * Hosts one micro-frontend for the current path. Route access drives the UI only; every backend operation the
 * micro-app performs is authorized again by the BFF and the business service.
 */
export function ModuleHost({path, className = ''}: {path: string; className?: string}) {
  const {context, locale, direction, t} = useShell();
  const container = useRef<HTMLDivElement>(null);
  const mounted = useRef<{instance: MountedInstance; moduleKey: string; port: ReturnType<typeof events.port>} | null>(null);
  const resolution = resolve(context, path);
  const access = resolution ? routeAccess(resolution.route, resolution.module, context) : null;
  const [status, setStatus] = useState<Status>('LOADING');
  const [error, setError] = useState<string | null>(null);
  const moduleKey = resolution && access === 'ALLOWED' ? resolution.module.moduleKey : null;

  const input = (res: Resolution, owner: {port: ReturnType<typeof events.port>; instanceId: string}): MountContextInput => ({
    instanceId: owner.instanceId,
    slotId: 'main', route: path, params: res.params, locale, direction, context,
    permissions: scopedPermissions(context, APPLICATION),
    events: owner.port,
    basePath: '/', navigate: next => navigate(next),
  });

  // Mount when the module changes, unmount when leaving it.
  useEffect(() => {
    if (!resolution) { setStatus(context.authenticated ? 'NOT_FOUND' : 'LOGIN_REQUIRED'); return; }
    if (access === 'LOGIN_REQUIRED') { setStatus('LOGIN_REQUIRED'); return; }
    if (access === 'DENIED') { setStatus('DENIED'); return; }
    let cancelled = false;
    const host = container.current;
    if (!host || !moduleKey) return;
    setStatus('LOADING');
    const instanceId = crypto.randomUUID();
    const port = events.port({instanceId, workspaceId: 'haramyar', applicationKey: APPLICATION});
    runtime.mount(host, resolution.module, input(resolution, {port, instanceId}))
      .then(instance => {
        if (cancelled) { void instance.unmount(); port.release(); return; }
        mounted.current = {instance, moduleKey, port};
        setStatus('MOUNTED');
      })
      .catch(failure => {
        port.release();
        if (cancelled) return;
        console.error('[haramyar] module failed', failure);
        setError(failure instanceof Error ? failure.message : String(failure));
        setStatus('ERROR');
      });
    return () => {
      cancelled = true;
      const current = mounted.current;
      mounted.current = null;
      if (current) { void current.instance.unmount(); current.port.release(); }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [moduleKey, access]);

  // Same module, new route, language or context: update in place.
  useEffect(() => {
    const current = mounted.current;
    if (current && resolution && current.moduleKey === resolution.module.moduleKey) void current.instance.update(input(resolution, {port: current.port, instanceId: current.instance.instanceId})).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [path, locale, direction, context]);

  return (
    <div className={`module-slot ${className}`} data-module={resolution?.module.moduleKey ?? 'none'} data-status={status}>
      {status === 'LOADING' && <Loading label={t('loading')} />}
      {status === 'LOGIN_REQUIRED' && <SlotState icon="lock" title={t('signInRequired')} hint={t('signInRequiredHint')} action={<Button onClick={() => auth.login(path)} testId="slot-sign-in">{t('signIn')}</Button>} />}
      {status === 'DENIED' && <SlotState icon="lock" title={t('accessDenied')} hint={t('accessDeniedHint')} action={<Button variant="ghost" onClick={() => navigate('/')}>{t('home')}</Button>} />}
      {status === 'NOT_FOUND' && <SlotState icon="search" title={t('notFound')} hint={t('notFoundHint')} action={<Button variant="ghost" onClick={() => navigate('/')}>{t('home')}</Button>} />}
      {status === 'ERROR' && <SlotState icon="alert" title={t('moduleUnavailable')} hint={error ?? t('errorHint')} action={<Button variant="ghost" onClick={() => location.reload()}>{t('retry')}</Button>} />}
      {/* The micro-app owns this element (inside its shadow root); React never renders children into it. */}
      <div ref={container} key={moduleKey ?? 'empty'} hidden={status !== 'MOUNTED' && status !== 'LOADING'} />
    </div>
  );
}

export function SlotState({icon, title, hint, action}: {icon: 'lock' | 'search' | 'alert'; title: string; hint: string; action?: React.ReactNode}) {
  return (
    <div className="module-slot-state" role="status">
      <div><span className="lock-badge"><Icon name={icon} size={30} /></span><h1>{title}</h1><p>{hint}</p>{action}</div>
    </div>
  );
}
