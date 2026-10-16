import type {AnyHiveContext, HiveContext} from '@hive-platform/contracts';
import {createAuth} from '@hive-platform/auth';
import {permissionsOf} from '@hive-platform/authorization';
import {createHttpClient, HiveHttpError} from '@hive-platform/http-client';
import {MfeRuntime} from '@hive-platform/mfe-runtime';
import {HiveEventHub} from '@hive-platform/workspace';

/** Hive SDK singletons. The browser holds only the HttpOnly session cookie; tokens stay in the BFF vault. */
export const http = createHttpClient();
export const auth = createAuth(http);
export const runtime = new MfeRuntime({onDiagnostic: d => console.warn('[hive]', d.code, d.message)});
export const events = new HiveEventHub(d => console.warn('[hive]', d.code, d.message));
export const APPLICATION = 'haramyar';

export function can(context: AnyHiveContext, resource: string, action: string): boolean {
  return permissionsOf(context).can(APPLICATION, resource, action);
}

/** A signed-in user is a member of HaramYar once Hive reports at least one HaramYar permission. */
export function isMember(context: AnyHiveContext): context is HiveContext {
  return context.authenticated && Object.keys(context.permissions).some(key => key.startsWith(`${APPLICATION}:`));
}

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

/**
 * Loads the current context. A newly signed-in user is enrolled idempotently into the default business role through
 * the HaramYar onboarding operation (Hive HYBRID route); the context is then re-read until the authorization graph
 * reflects the new grants (projection is asynchronous, normally sub-second).
 */
export async function loadContext(onSettingUp?: () => void): Promise<AnyHiveContext> {
  let context = await auth.currentContext();
  if (!context.authenticated || isMember(context)) return context;
  onSettingUp?.();
  try {
    await http.post(`/api/routes/${APPLICATION}/onboarding`);
  } catch (error) {
    if (!(error instanceof HiveHttpError) || error.status >= 500 || error.status === 0) console.warn('[haramyar] onboarding unavailable', error);
    return context;
  }
  for (let attempt = 0; attempt < 12 && !isMember(context); attempt++) {
    await sleep(attempt < 4 ? 250 : 750);
    context = await auth.currentContext();
  }
  return context;
}

/** Ends the Hive session (and the identity provider session) and removes every HaramYar tab-local draft. */
export async function signOut(): Promise<void> {
  try {
    await auth.logout();
  } finally {
    try {
      for (const key of Object.keys(sessionStorage)) if (key.startsWith('haramyar.')) sessionStorage.removeItem(key);
    } catch { /* storage unavailable */ }
    navigator.serviceWorker?.controller?.postMessage('haramyar:signed-out');
  }
}
