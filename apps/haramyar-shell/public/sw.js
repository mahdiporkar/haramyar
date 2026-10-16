/*
 * HaramYar service worker. Fixes the prototype's caching of every successful same-origin GET.
 *
 * Only public, static, non-personal resources are ever cached:
 *   - the app shell document (fallback for offline navigation; it contains no user data),
 *   - built static assets (/static/**), brand assets, fonts and the web manifest.
 * Never cached: /api/** (contexts, sessions, routes, micro-frontend artifacts), /auth/**, OAuth callbacks,
 * Keycloak (/realms/**, /resources/**), the operator console, server-rendered pages and anything else.
 * On sign-out the shell asks the worker to drop its runtime cache as an extra safeguard.
 */
const VERSION = 'haramyar-v2';
const SHELL_CACHE = `${VERSION}-shell`;
const STATIC_CACHE = `${VERSION}-static`;
const PRECACHE = ['/', '/manifest.webmanifest', '/assets/fonts/Vazirmatn-Variable.woff2', '/assets/brand/haramyar-symbol.webp', '/assets/brand/haramyar-app-icon-192.png'];
const NEVER = [/^\/api\//, /^\/auth\//, /^\/oauth2\//, /^\/login\//, /^\/realms\//, /^\/resources\//, /^\/console\//, /^\/actuator\//, /^\/martyr(\/|$)/, /^\/sitemap\.xml$/, /^\/robots\.txt$/];
const STATIC = [/^\/static\//, /^\/assets\//, /^\/manifest\.webmanifest$/];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(SHELL_CACHE).then(cache => cache.addAll(PRECACHE)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => !k.startsWith(VERSION)).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});

self.addEventListener('message', event => {
  if (event.data === 'haramyar:signed-out') event.waitUntil(caches.delete(STATIC_CACHE));
});

self.addEventListener('fetch', event => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (NEVER.some(pattern => pattern.test(url.pathname))) return; // network only, never stored
  if (request.headers.has('authorization')) return;

  if (request.mode === 'navigate') {
    // Network first; offline fallback is the public app shell (no personal data).
    event.respondWith(fetch(request).catch(() => caches.match('/', {cacheName: SHELL_CACHE}).then(cached => cached ?? Response.error())));
    return;
  }

  if (STATIC.some(pattern => pattern.test(url.pathname))) {
    event.respondWith(caches.open(STATIC_CACHE).then(async cache => {
      const cached = await cache.match(request);
      if (cached) return cached;
      const response = await fetch(request);
      const cacheable = response.ok && response.type === 'basic' && !/no-store|private/i.test(response.headers.get('Cache-Control') ?? '') && !response.headers.has('Set-Cookie');
      if (cacheable) await cache.put(request, response.clone());
      return response;
    }));
  }
});
