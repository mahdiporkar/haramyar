import {before, after, test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, mkdirSync} from 'node:fs';
import {chromium} from 'playwright';

const base = process.env.HARAMYAR_E2E_BASE ?? 'http://localhost:8080';
const covers = JSON.parse(readFileSync(new URL('../../docs/martyrs/salim-covers.json', import.meta.url)));
const shots = process.env.MEMORIAL_SCREENSHOTS ?? 'test-results/memorial';
let browser;
before(async () => {
  mkdirSync(shots, {recursive: true});
  browser = await chromium.launch({...(process.platform === 'win32' ? {channel: 'msedge'} : {})});
});
after(async () => browser?.close());

for (const locale of ['fa', 'ar', 'en']) for (const width of [1366, 390]) {
  test(`${locale} ${width}: covers, biography, SEO, responsive layout and library controls`, async () => {
    const context = await browser.newContext({viewport: {width, height: 900}, reducedMotion: 'reduce'});
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    assert.equal((await page.goto(`${base}/martyr/izz-al-din-salim?lang=${locale}`)).status(), 200);
    await page.evaluate(() => document.fonts.ready);
    assert.equal(await page.locator('html').getAttribute('dir'), locale === 'en' ? 'ltr' : 'rtl');
    assert.equal(await page.locator('.book-card').count(), 45);
    assert.equal(await page.locator('.book-card[data-fallback="true"]').count(), locale === 'ar' ? 0 : 36);
    assert.equal(await page.locator('.book-cover-image').count(), 43);
    assert.equal(await page.locator('.book-art[data-cover-status="missing"]').count(), 2);
    assert.equal(await page.locator('link[rel="alternate"]').count(), 3);
    assert.ok((await page.locator('link[rel="canonical"]').getAttribute('href')).endsWith(`?lang=${locale}`));
    const profile = await (await context.request.get(`${base}/api/routes/martyrs/public/profiles/izz-al-din-salim?locale=${locale}`)).json();
    const biography = await page.locator('.biography-prose [itemprop="description"]').innerText();
    assert.equal(biography.replace(/\s+/g, ' ').trim(), profile.biography.replace(/\s+/g, ' ').trim());
    assert.equal(await page.locator('.hero-portrait img').evaluate(i => i.complete && i.naturalWidth > 0), true);
    await page.screenshot({path: `${shots}/${locale}-${width}-hero.png`});
    await page.locator('#biography').evaluate(e => e.scrollIntoView({block: 'start'}));
    await page.screenshot({path: `${shots}/${locale}-${width}-biography.png`});
    await page.locator('#library').evaluate(e => e.scrollIntoView({block: 'start'}));
    for (const cover of covers) {
      const card = page.locator(`[data-book-id="${cover.bookId}"]`);
      assert.equal(await card.count(), 1);
      if (cover.matchStatus === 'verified') {
        const img = card.locator('img');
        assert.ok((await img.getAttribute('alt')).includes(cover.bookTitle));
        assert.equal(await img.getAttribute('src'), `/martyr/_assets/covers/${cover.web180}`);
        assert.ok((await img.getAttribute('srcset')).includes(cover.storedImage));
        await img.scrollIntoViewIfNeeded();
        await img.evaluate(i => i.decode());
      } else assert.equal(await card.locator('.cover-placeholder').isVisible(), true);
    }
    await page.locator('#library').evaluate(e => e.scrollIntoView({block: 'start'}));
    await page.screenshot({path: `${shots}/${locale}-${width}-library.png`});
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false);
    await page.locator('#library-search').fill('zzzz-no-book-123');
    assert.equal(await page.locator('.book-card:visible').count(), 0);
    assert.equal(await page.locator('#no-results').isVisible(), true);
    await page.locator('#library-search').fill('');
    assert.equal(await page.locator('.book-card:visible').count(), 45);
    await page.locator('#library-search').fill('وصيه');
    assert.ok(await page.locator('.book-card:visible').count() > 0, 'Arabic normalized search matches the known testament title');
    await page.locator('#library-search').fill('');
    if (locale !== 'ar') {
      await page.locator('#translated-only').check();
      assert.equal(await page.locator('.book-card:visible').count(), 9);
      await page.locator('#translated-only').uncheck();
    }
    await page.locator('#references').scrollIntoViewIfNeeded();
    await page.screenshot({path: `${shots}/${locale}-${width}-references.png`});
    assert.deepEqual(errors, []);
    assert.equal(await page.locator('.hero-copy').evaluate(e => getComputedStyle(e).animationName), 'none');
    await page.locator('.skip-link').focus();
    await page.keyboard.press('Enter');
    assert.equal(await page.evaluate(() => location.hash), '#main-content');
    await context.close();
  });
}

test('Image failure keeps an honest fallback and downloads; no-JS catalogue stays complete', async () => {
  const context = await browser.newContext({viewport: {width: 320, height: 800}});
  const page = await context.newPage();
  await page.route('**/martyr/_assets/covers/**', route => route.abort());
  await page.goto(`${base}/martyr/izz-al-din-salim?lang=en`);
  const card = page.locator('.book-card').first();
  await card.scrollIntoViewIfNeeded();
  await page.waitForFunction(() => document.querySelector('.book-card .book-cover-image').hidden);
  assert.equal(await card.locator('.cover-placeholder').isVisible(), true);
  assert.equal(await card.locator('[data-primary]').isVisible(), true);
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false);
  await context.close();
  const nojs = await browser.newContext({javaScriptEnabled: false});
  const staticPage = await nojs.newPage();
  await staticPage.goto(`${base}/martyr/izz-al-din-salim?lang=fa`);
  assert.equal(await staticPage.locator('.book-card').count(), 45);
  assert.equal(await staticPage.locator('[data-primary]').count(), 45);
  assert.equal(await staticPage.locator('[data-library-controls]').isVisible(), false);
  await nojs.close();
});

test('All responsive cover assets are first-party WebP with immutable caching', async () => {
  const context = await browser.newContext();
  for (const cover of covers.filter(c => c.matchStatus === 'verified')) for (const file of [cover.web180, cover.web360]) {
    const response = await context.request.get(`${base}/martyr/_assets/covers/${file}`);
    assert.equal(response.status(), 200);
    assert.equal(response.headers()['content-type'], 'image/webp');
    // The local preview proxy does not forward cache headers.
    if (base.startsWith('https:')) assert.match(response.headers()['cache-control'], /immutable/);
    assert.equal((await response.body()).subarray(8, 12).toString(), 'WEBP');
  }
  assert.equal((await context.request.get(`${base}/martyr/_assets/covers/not-in-manifest.webp`)).status(), 404);
  await context.close();
});
