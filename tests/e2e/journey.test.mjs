// HaramYar end-to-end journey against the real stack: Keycloak registration and login (customer and staff with TOTP),
// Hive BFF sessions, routes and authorization, both micro-frontends, asynchronous notifications, editorial workflow,
// server-rendered public pages, themes, languages, PWA caching and mobile layout.
// Prerequisite: the dev stack is up and bootstrapped with HARAMYAR_DEV_FIXTURES=true (docs/DEPLOYMENT.md).
import {after, before, test} from 'node:test';
import assert from 'node:assert/strict';
import {BASE, ENV, csrfFetch, keycloakLogin, launch, resetStaffMfa, until} from './support.mjs';

const STAFF_PASSWORD = ENV.HARAMYAR_FIXTURE_STAFF_PASSWORD;
const run = Date.now().toString().slice(-7);
const customer = {national: `0912${run}`, e164: `+98912${run}`, spaced: `+98 912 ${run.slice(0, 3)} ${run.slice(3)}`, password: `Customer-${run}9x`};
const other = {national: `0935${run}`, password: `Second-${run}7y`};
const secrets = {};
const slug = `e2e-fixture-${run}`;
let browser;
const state = {};

before(async () => {
  assert.ok(STAFF_PASSWORD, 'deployment/.env must enable development staff fixtures');
  await resetStaffMfa(['staff.coordinator', 'staff.editor', 'staff.reviewer', 'staff.admin']);
  browser = await launch();
});
after(async () => { await browser?.close(); });

async function newPage(options = {}) {
  const context = await browser.newContext({locale: 'fa-IR', ...options});
  const page = await context.newPage();
  page.on('pageerror', error => console.error('[pageerror]', error.message));
  return page;
}

async function signInStaff(username) {
  const page = await newPage();
  await page.goto(`${BASE}/account`);
  await page.getByTestId('sign-in').click();
  await keycloakLogin(page, username, STAFF_PASSWORD, secrets);
  await page.waitForURL(`${BASE}/account`);
  await page.getByTestId('account-name').waitFor();
  return page;
}

async function register(page, mobile, password) {
  await page.waitForURL(/\/realms\/haramyar\//);
  await page.click('#kc-registration a');
  await page.fill('#username', mobile);
  await page.fill('#password', password);
  await page.fill('#password-confirm', password);
  await page.check('#termsAccepted');
  await page.click('input[type=submit]');
}

test('anonymous visitors browse public content; protected pages and APIs require sign-in', async () => {
  const page = await newPage();
  await page.goto(BASE);
  await page.getByTestId('splash').waitFor({state: 'detached', timeout: 15000});
  assert.equal(await page.locator('html').getAttribute('dir'), 'rtl');
  await page.getByTestId('service-martyrs').waitFor();
  await page.getByTestId('lang-en').click();
  assert.equal(await page.locator('html').getAttribute('dir'), 'ltr');
  assert.equal((await page.getByTestId('nav-home').innerText()).trim(), 'Home');
  await page.getByTestId('lang-ar').click();
  assert.equal(await page.locator('html').getAttribute('lang'), 'ar');
  await page.getByTestId('lang-fa').click();

  await page.getByTestId('service-booking').click();
  await page.waitForURL(`${BASE}/rozeh`);
  await page.getByTestId('start-request').waitFor();
  await page.goto(`${BASE}/rozeh/requests`);
  await page.getByTestId('slot-sign-in').waitFor();
  await page.goto(`${BASE}/rozeh/coordination`);
  await page.getByTestId('slot-sign-in').waitFor();

  const denied = await csrfFetch(page, 'POST', '/api/routes/rozeh/my/bookings', {});
  assert.equal(denied.status, 401, denied.body);
  const coordination = await page.evaluate(async () => (await fetch('/api/routes/rozeh/coordination/bookings')).status);
  assert.equal(coordination, 401);
  const context = await page.evaluate(async () => (await fetch('/api/public/context')).json());
  assert.deepEqual(context.modules.map(m => m.moduleKey).sort(), ['martyrs', 'rozeh-booking']);
  assert.ok(!JSON.stringify(context).includes('coordination'), 'public context discloses no protected routes');
  await page.context().close();
});

test('a visitor fills the wizard, registers with a mobile number through Keycloak and submits a real request', async () => {
  const page = await newPage();
  await page.goto(`${BASE}/rozeh/request`);
  await page.getByTestId('wizard-next').waitFor();
  await page.locator('input[name=hostName]').fill('میزبان آزمایشی (E2E)');
  await page.locator('input[name=hostPhone]').fill(customer.national);
  await page.getByTestId('wizard-next').click();
  await page.locator('input[name=city]').fill('قم');
  await page.locator('input[name=address]').fill('خیابان آزمایشی، پلاک ۷ (E2E)');
  await page.getByRole('button', {name: 'خانوادگی'}).click();
  await page.getByTestId('wizard-next').click();
  await page.getByRole('button', {name: 'ماه بعد'}).click();
  await page.locator('[data-date]:not([disabled])').nth(9).click();
  await page.getByTestId('wizard-next').click();
  await page.getByTestId('submit-request').click();

  await register(page, customer.national, customer.password);
  await page.waitForURL(`${BASE}/rozeh/request`, {timeout: 30000});
  await page.getByTestId('submit-request').click();
  await page.waitForURL(/\/rozeh\/requests\/[0-9a-f-]{36}$/, {timeout: 30000});
  // Shown with Persian digits in the fa locale; compare in ASCII.
  const tracking = (await page.getByTestId('tracking-code').innerText()).trim().replace(/[۰-۹]/g, d => String(d.charCodeAt(0) - 0x06f0));
  assert.match(tracking, /^RZ-[0-9A-Z]{4}-[0-9A-Z]{4}$/);
  state.bookingId = page.url().split('/').pop();
  state.tracking = tracking;
  state.customer = page;

  const cookies = await page.context().cookies();
  assert.ok(cookies.find(c => c.name === 'HIVE_SESSION')?.httpOnly, 'session cookie is HttpOnly');
  assert.equal(await page.evaluate(() => document.cookie.includes('HIVE_SESSION')), false, 'no session material is readable by scripts');
  assert.equal(await page.evaluate(() => Object.keys(localStorage).some(k => /token/i.test(k) || /eyJ/.test(localStorage.getItem(k) ?? ''))), false);

  await page.goto(`${BASE}/activity`);
  await page.locator(`[data-booking-id="${state.bookingId}"]`).waitFor();
});

test('the same mobile number in another spelling cannot create a second account', async () => {
  const page = await newPage();
  await page.goto(`${BASE}/account`);
  await page.getByTestId('sign-in').click();
  await register(page, customer.spaced, `Another-${run}5z`);
  const error = page.locator('#input-error-username, [id^=input-error], .kc-feedback-text').first();
  await error.waitFor();
  assert.match(await error.innerText(), /قبلاً/, 'refused because the normalized number already has an account');
  assert.match(page.url(), /\/realms\/haramyar\//);
  await page.context().close();
});

test('a coordinator signs in with mandatory TOTP enrollment and accepts the request', async () => {
  const page = await signInStaff('staff.coordinator');
  assert.ok(secrets['staff.coordinator'], 'TOTP enrollment was required for the staff account');
  await page.getByTestId('workspace-coordination').click();
  await page.waitForURL(`${BASE}/rozeh/coordination`);
  await page.locator(`[data-booking-id="${state.bookingId}"]`).click();
  await page.getByTestId('coord-accept').click();
  await page.getByTestId('coordinator-actions').getByText('پذیرش درخواست').waitFor({state: 'detached'});
  assert.equal(await page.getByTestId('coord-confirm').count(), 0, 'final confirmation belongs to the host under the provisional policy');
  state.coordinator = page;
});

test('the host is notified asynchronously, confirms, and the history is recorded', async () => {
  const page = state.customer;
  await page.goto(`${BASE}/`);
  // The submission notice already lights the badge; wait for the acceptance notice itself.
  await until(async () => {
    await page.reload();
    await page.locator('.notify .count').waitFor({timeout: 4000}).catch(() => undefined);
    await page.getByTestId('open-notifications').click();
    return page.locator('[data-kind="BOOKING_ACCEPTED"]').waitFor({timeout: 3000}).then(() => true, () => false);
  }, {timeout: 45000, interval: 500, message: 'booking acceptance notification via RabbitMQ'});
  await page.locator('[data-kind="BOOKING_ACCEPTED"]').click();
  await page.waitForURL(`${BASE}/rozeh/requests/${state.bookingId}`);
  await page.getByTestId('confirm-booking').click();
  await page.locator('[data-status="CONFIRMED"]').first().waitFor();
  const actions = await page.getByTestId('timeline').locator('[data-action]').evaluateAll(nodes => nodes.map(n => n.getAttribute('data-action')));
  assert.deepEqual(actions, ['REQUEST', 'ACCEPT', 'CONFIRM']);
});

test('customers cannot reach coordination, and hosts cannot see each other\'s bookings', async () => {
  const page = state.customer;
  await page.goto(`${BASE}/rozeh/coordination`);
  await page.locator('[data-status="DENIED"]').waitFor();
  const api = await page.evaluate(async () => (await fetch('/api/routes/rozeh/coordination/bookings')).status);
  assert.equal(api, 403);
  const escalation = await csrfFetch(page, 'POST', `/api/routes/rozeh/coordination/bookings/${state.bookingId}/cancel`, {reason: 'escalation attempt'});
  assert.equal(escalation.status, 403);

  const second = await newPage();
  await second.goto(`${BASE}/account`);
  await second.getByTestId('sign-in').click();
  await register(second, other.national, other.password);
  await second.waitForURL(`${BASE}/account`, {timeout: 30000});
  await until(async () => (await second.evaluate(async () => (await fetch('/api/routes/rozeh/my/bookings')).status)) === 200, {message: 'onboarding grants'});
  const foreign = await second.evaluate(async id => (await fetch(`/api/routes/rozeh/my/bookings/${id}`)).status, state.bookingId);
  assert.equal(foreign, 404);
  const list = await second.evaluate(async () => (await fetch('/api/routes/rozeh/my/bookings')).json());
  assert.equal(list.total, 0);
  await second.context().close();
});

test('editorial workflow: draft and submit by an editor, publication by a different reviewer, public SEO page', async () => {
  const editor = await signInStaff('staff.editor');
  await editor.goto(`${BASE}/martyrs/editorial`);
  await editor.getByTestId('new-profile').click();
  await editor.locator('input[name=fullName]').first().fill('پرونده آزمایشی توسعه (E2E fixture)');
  await editor.locator('input[name=slug]').fill(slug);
  await editor.getByTestId('create-profile').click();
  await editor.waitForURL(/\/martyrs\/editorial\/[0-9a-f-]{36}$/);
  state.profileId = editor.url().split('/').pop();
  await editor.locator('textarea[name=shortDescription]').fill('متن آزمایشی برای آزمون خودکار؛ محتوای واقعی نیست.');
  await editor.locator('textarea[name=biography]').fill('این زندگی‌نامه یک داده آزمایشی است و به هیچ شخص واقعی نسبت داده نمی‌شود.');
  await editor.getByTestId('save-profile').click();
  await editor.locator('input[name=sourceTitle]').fill('منبع آزمایشی (fixture)');
  await editor.getByTestId('add-source').click();
  await editor.locator('.sources li').first().waitFor();
  assert.equal((await fetch(`${BASE}/martyr/${slug}`)).status, 404, 'drafts are never public');
  await editor.getByTestId('submit-review').click();
  await editor.locator('[data-status="IN_REVIEW"]').waitFor();
  assert.equal(await editor.getByTestId('publish').count(), 0, 'editors cannot publish');
  const selfPublish = await csrfFetch(editor, 'POST', `/api/routes/martyrs/editorial/profiles/${state.profileId}/publish`, {});
  assert.equal(selfPublish.status, 403);

  const reviewer = await signInStaff('staff.reviewer');
  await reviewer.goto(`${BASE}/martyrs/editorial/${state.profileId}`);
  await reviewer.getByTestId('publish').click();
  await reviewer.locator('[data-status="PUBLISHED"]').waitFor();

  const ssr = await fetch(`${BASE}/martyr/${slug}`);
  assert.equal(ssr.status, 200);
  const html = await ssr.text();
  assert.match(html, new RegExp(`<link rel="canonical" href="[^"]*/martyr/${slug}"`));
  assert.match(html, /property="og:title"/);
  assert.match(html, /application\/ld\+json/);
  assert.match(await (await fetch(`${BASE}/sitemap.xml`)).text(), new RegExp(`/martyr/${slug}<`));

  const visitor = await newPage();
  await visitor.goto(`${BASE}/martyrs/profiles/${slug}`);
  await visitor.getByTestId('profile-name').waitFor();
  await visitor.context().close();
  await editor.context().close();
  await reviewer.context().close();
});

test('a business administrator schedules a mourning occasion and every visitor sees the theme', async () => {
  const admin = await signInStaff('staff.admin');
  await admin.getByTestId('workspace-occasions').click();
  await admin.waitForURL(`${BASE}/admin/occasions`);
  const now = Date.now();
  const created = await csrfFetch(admin, 'POST', '/api/routes/haramyar/admin/occasions', {key: `e2e-${run}`, theme: 'MOURNING', titles: {fa: 'مناسبت آزمایشی', en: 'Test occasion'},
    startsAt: new Date(now - 60000).toISOString(), endsAt: new Date(now + 3600000).toISOString(), priority: 900});
  assert.equal(created.status, 201, created.body);
  const id = JSON.parse(created.body).id;
  assert.equal((await csrfFetch(admin, 'POST', `/api/routes/haramyar/admin/occasions/${id}/schedule`)).status, 200);
  const visitor = await newPage();
  await until(async () => {
    await visitor.goto(BASE);
    return visitor.waitForFunction(() => document.documentElement.dataset.theme === 'mourning', null, {timeout: 4000}).then(() => true, () => false);
  }, {timeout: 120000, interval: 5000, message: 'mourning theme (public theme is cached for up to 60 s)'});
  assert.equal((await csrfFetch(admin, 'POST', `/api/routes/haramyar/admin/occasions/${id}/archive`)).status, 200);
  const customerDenied = await csrfFetch(state.customer, 'POST', '/api/routes/haramyar/admin/occasions', {key: 'x-y-z'});
  assert.equal(customerDenied.status, 403);
  await visitor.context().close();
  await admin.context().close();
});

test('the service worker never stores private responses, and sign-out ends the session', async () => {
  const page = state.customer;
  await page.goto(`${BASE}/rozeh/requests/${state.bookingId}`);
  await page.waitForFunction(() => navigator.serviceWorker?.controller != null, null, {timeout: 20000}).catch(() => undefined);
  await page.goto(`${BASE}/activity`);
  const cached = await page.evaluate(async () => {
    const urls = [];
    for (const name of await caches.keys()) for (const request of await (await caches.open(name)).keys()) urls.push(new URL(request.url).pathname);
    return urls;
  });
  assert.ok(cached.every(path => !/^\/(api|auth|oauth2|login|realms)\//.test(path)), `private paths cached: ${cached.join(', ')}`);
  await page.goto(`${BASE}/account`);
  await page.getByTestId('sign-out').click();
  await page.waitForURL(`${BASE}/`);
  await page.goto(`${BASE}/account`);
  await page.getByTestId('sign-in').waitFor();
  assert.equal(await page.evaluate(async () => (await fetch('/api/me/context')).status), 401);
  // The identity provider session ended too, and login normalizes another spelling of the same number.
  await page.getByTestId('sign-in').click();
  await keycloakLogin(page, customer.spaced, customer.password);
  await page.getByTestId('account-name').waitFor();
  await page.context().close();
});

test('mobile layout: bottom navigation, no horizontal overflow, RTL', async () => {
  const page = await newPage({viewport: {width: 390, height: 844}, isMobile: true, hasTouch: true});
  await page.goto(BASE);
  await page.getByTestId('splash').waitFor({state: 'detached', timeout: 15000});
  await page.getByTestId('nav-services').waitFor();
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'no horizontal scroll');
  await page.getByTestId('nav-services').click();
  await page.waitForURL(`${BASE}/services`);
  await page.goto(`${BASE}/rozeh/request`);
  await page.getByTestId('wizard-next').waitFor();
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'wizard fits the phone width');
  await page.context().close();
});
