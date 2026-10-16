// Shared helpers for the end-to-end journey. Secrets are read from deployment/.env (generated locally, never committed).
import {createHmac} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {chromium} from 'playwright';

export const BASE = process.env.HARAMYAR_E2E_BASE ?? 'http://localhost:8080';
export const ENV = Object.fromEntries(readFileSync(new URL('../../deployment/.env', import.meta.url), 'utf8').split('\n')
  .filter(line => line.includes('=')).map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));

export function launch() {
  return chromium.launch({headless: process.env.HEADED !== '1', ...(process.platform === 'win32' ? {channel: 'msedge'} : {})});
}

/** RFC 6238 TOTP (SHA-1, 6 digits, 30 s) as configured in the realm's OTP policy. */
export function totp(secretBase32, now = Date.now()) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const clean = secretBase32.replace(/[\s=]/g, '').toUpperCase();
  let bits = '';
  for (const c of clean) bits += alphabet.indexOf(c).toString(2).padStart(5, '0');
  const key = Buffer.from(bits.match(/.{8}/g).map(b => parseInt(b, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(now / 30000)));
  const hmac = createHmac('sha1', key).update(counter).digest();
  const offset = hmac[hmac.length - 1] & 0xf;
  return String((hmac.readUInt32BE(offset) & 0x7fffffff) % 1_000_000).padStart(6, '0');
}

export async function until(check, {timeout = 20000, interval = 300, message = 'condition'} = {}) {
  const deadline = Date.now() + timeout;
  let last;
  while (Date.now() < deadline) {
    try { last = await check(); if (last) return last; } catch (error) { last = error; }
    await new Promise(r => setTimeout(r, interval));
  }
  throw new Error(`Timed out waiting for ${message}${last instanceof Error ? `: ${last.message}` : ''}`);
}

/** Keycloak admin (development stack only): resets staff MFA so enrollment is exercised on every run. */
export async function resetStaffMfa(usernames) {
  const admin = await keycloakAdmin();
  for (const username of usernames) {
    const [user] = await admin(`/users?exact=true&username=${encodeURIComponent(username)}`);
    for (const credential of await admin(`/users/${user.id}/credentials`)) if (credential.type === 'otp') await admin(`/users/${user.id}/credentials/${credential.id}`, {method: 'DELETE'});
    await admin(`/users/${user.id}`, {method: 'PUT', body: JSON.stringify({...user, requiredActions: ['CONFIGURE_TOTP']})});
  }
}

/** The gateway never exposes the Keycloak admin API, so tests reach it on the internal network through a short-lived curl container. */
async function keycloakAdmin() {
  const {execFileSync} = await import('node:child_process');
  const run = (method, path, body) => execFileSync('docker', ['run', '--rm', '--network', 'haramyar_internal', 'curlimages/curl:8.16.0', '-s', '-X', method,
    ...(path.startsWith('TOKEN') ? ['-d', `grant_type=password&client_id=admin-cli&username=${ENV.KEYCLOAK_ADMIN_USER}&password=${ENV.KEYCLOAK_ADMIN_PASSWORD}`, 'http://keycloak:8080/realms/master/protocol/openid-connect/token']
      : ['-H', `Authorization: Bearer ${token}`, '-H', 'Content-Type: application/json', ...(body ? ['-d', body] : []), `http://keycloak:8080/admin/realms/haramyar${path}`])], {encoding: 'utf8'});
  const token = JSON.parse(run('POST', 'TOKEN')).access_token;
  return async (path, init = {}) => {
    const text = run(init.method ?? 'GET', path, init.body);
    return text ? JSON.parse(text) : null;
  };
}

/** Completes the Keycloak login form, including staff TOTP enrollment or entry. Returns the enrolled secret if any. */
export async function keycloakLogin(page, username, password, secrets = {}) {
  await page.waitForURL(/\/realms\/haramyar\//);
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('#kc-login');
  await page.waitForLoadState('domcontentloaded');
  if (await page.locator('#kc-totp-settings-form').count()) {
    if (!(await page.locator('#kc-totp-secret-key').count())) await page.click('#mode-manual');
    const secret = (await page.locator('#kc-totp-secret-key').innerText()).trim();
    secrets[username] = secret;
    await page.fill('#totp', totp(secret));
    if (await page.locator('#userLabel').count()) await page.fill('#userLabel', 'e2e');
    await page.click('#saveTOTPBtn');
  } else if (await page.locator('#otp').count()) {
    await page.fill('#otp', totp(secrets[username]));
    await page.click('#kc-login');
  }
  await page.waitForURL(url => !url.pathname.startsWith('/realms/'), {timeout: 30000});
  return secrets[username];
}

export async function csrfFetch(page, method, path, body) {
  return page.evaluate(async ({method, path, body}) => {
    const csrf = await (await fetch('/auth/csrf')).json();
    const response = await fetch(path, {method, headers: {[csrf.headerName]: csrf.token, 'Content-Type': 'application/json', Accept: 'application/json'}, body: body ? JSON.stringify(body) : undefined});
    return {status: response.status, body: await response.text()};
  }, {method, path, body});
}
