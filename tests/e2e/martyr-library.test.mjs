// Public multilingual library of a martyr's works, as an anonymous visitor sees it (no credentials needed):
// every book appears in every site language; the edition in the site language is primary, otherwise the Arabic
// original is shown and labelled as a fallback; every listed PDF is the exact published source file.
//   HARAMYAR_E2E_BASE=https://haramyar.com npm run test:library -w tests/e2e
// The expected corpus is docs/martyrs/salim-editions-inventory.json (titles and SHA-256 of every edition).
import {after, before, test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {chromium} from 'playwright';

const BASE = process.env.HARAMYAR_E2E_BASE ?? 'http://localhost:8080';
const inventory = JSON.parse(readFileSync(new URL('../../docs/martyrs/salim-editions-inventory.json', import.meta.url), 'utf8'));
const LOCALES = ['fa', 'ar', 'en'];
const FALLBACK = {
  fa: 'ترجمهٔ فارسی این کتاب هنوز موجود نیست؛ نسخهٔ اصلی عربی در دسترس است.',
  en: 'An English translation is not yet available. The original Arabic edition is available.',
};
const PRIMARY = {fa: {fa: 'دانلود نسخهٔ فارسی', ar: 'دانلود نسخهٔ عربی'}, en: {en: 'Download English PDF', ar: 'Download Arabic PDF'}, ar: {ar: 'تحميل النسخة العربية'}};
const translated = inventory.books.filter(b => b.editions.length > 1);
const arabicOnly = inventory.books.filter(b => b.editions.length === 1);

let browser;
const pages = {};

/** Maps a rendered card back to the inventory book whose edition in the card's language carries the card's title. */
function bookOf(card) {
  const matches = inventory.books.filter(b => b.editions.some(e => e.locale === card.editionLocale && e.title === card.title));
  assert.equal(matches.length, 1, `card "${card.title}" (${card.editionLocale}) must match exactly one inventory book`);
  return matches[0];
}

async function render(locale, viewport) {
  const context = await browser.newContext({viewport});
  const page = await context.newPage();
  const response = await page.goto(`${BASE}/martyr/${inventory.profileSlug}?lang=${locale}`, {waitUntil: 'domcontentloaded'});
  assert.equal(response.status(), 200);
  const result = await page.evaluate(() => ({
    lang: document.documentElement.lang, dir: document.documentElement.dir,
    overflow: document.documentElement.scrollWidth > window.innerWidth + 1,
    cards: [...document.querySelectorAll('.book-card')].map(li => {
      const h3 = li.querySelector('h3'), primary = li.querySelector('a[data-primary="true"]');
      return {bookId: li.dataset.bookId, editionId: li.dataset.editionId, editionLocale: li.dataset.editionLocale, fallback: li.dataset.fallback === 'true',
        title: h3.textContent, titleLang: h3.lang, titleDir: h3.dir, notice: li.querySelector('.book-fallback')?.textContent ?? null,
        primaryHref: primary.getAttribute('href'), primaryText: primary.textContent, primaryHrefLang: primary.hreflang,
        others: [...li.querySelectorAll('.book-editions a')].map(a => ({locale: a.dataset.editionLocale, href: a.getAttribute('href'), lang: a.lang}))};
    }),
  }));
  await context.close();
  return result;
}

before(async () => {
  browser = await chromium.launch({headless: process.env.HEADED !== '1', ...(process.platform === 'win32' ? {channel: 'msedge'} : {})});
  for (const locale of LOCALES) pages[locale] = await render(locale, {width: 1366, height: 900});
});
after(async () => { await browser?.close(); });

for (const [label, locale, preferred] of [['A: Arabic', 'ar', 45], ['B: Persian', 'fa', translated.length], ['C: English', 'en', translated.length]]) {
  test(`${label} site lists every book with the ${locale} edition preferred and Arabic fallbacks labelled`, () => {
    const {cards, lang, dir} = pages[locale];
    assert.equal(lang, locale);
    assert.equal(dir, locale === 'en' ? 'ltr' : 'rtl');
    assert.equal(cards.length, inventory.books.length);
    assert.equal(new Set(cards.map(c => c.bookId)).size, inventory.books.length, 'one card per book');
    assert.equal(cards.filter(c => c.editionLocale === locale).length, preferred);
    assert.equal(cards.filter(c => c.fallback).length, inventory.books.length - preferred);
    for (const card of cards) {
      const book = bookOf(card);
      const expected = book.editions.some(e => e.locale === locale) ? locale : 'ar';
      assert.equal(card.editionLocale, expected, `${book.originalTitle}: selected edition`);
      assert.equal(card.titleLang, expected);
      assert.equal(card.titleDir, expected === 'en' ? 'ltr' : 'rtl');
      assert.equal(card.fallback, expected !== locale);
      assert.equal(card.notice, card.fallback ? FALLBACK[locale] : null);
      assert.equal(card.primaryText, PRIMARY[locale][expected]);
      assert.equal(card.primaryHref, `/api/routes/martyrs/public/books/${card.editionId}/file`);
      assert.equal(card.primaryHrefLang, expected);
      assert.deepEqual(card.others.map(o => o.locale).sort(), book.editions.map(e => e.locale).filter(l => l !== expected).sort(),
        `${book.originalTitle}: other editions are exactly the published ones`);
    }
  });
}

test('D: switching language keeps the book and changes the selected edition', () => {
  const book = translated.find(b => b.sourceId === '45') ?? translated[0];
  const ids = new Set();
  for (const locale of LOCALES) {
    const edition = book.editions.find(e => e.locale === locale);
    const card = pages[locale].cards.find(c => c.editionLocale === locale && c.title === edition.title);
    assert.ok(card, `${locale}: card for ${edition.title}`);
    assert.equal(card.fallback, false);
    ids.add(card.bookId);
    const editionIds = new Set(LOCALES.map(l => pages[l].cards.find(c => c.bookId === card.bookId).editionId));
    assert.equal(editionIds.size, 3, 'a different edition is primary in each language');
  }
  assert.equal(ids.size, 1, 'same book identity in every language');
});

test('E: an Arabic-only book stays visible in every language as the labelled Arabic original', () => {
  const book = arabicOnly[0];
  const title = book.editions[0].title;
  const cards = LOCALES.map(locale => pages[locale].cards.find(c => c.title === title));
  assert.ok(cards.every(Boolean));
  assert.equal(new Set(cards.map(c => c.bookId)).size, 1);
  assert.equal(new Set(cards.map(c => c.editionId)).size, 1);
  for (const [i, locale] of LOCALES.entries()) {
    assert.equal(cards[i].editionLocale, 'ar');
    assert.equal(cards[i].titleLang, 'ar');
    assert.equal(cards[i].notice, locale === 'ar' ? null : FALLBACK[locale]);
    assert.equal(cards[i].others.length, 0);
  }
});

test('mobile layout renders all books without horizontal overflow', async () => {
  for (const locale of LOCALES) {
    const mobile = await render(locale, {width: 390, height: 844});
    assert.equal(mobile.cards.length, inventory.books.length);
    assert.equal(mobile.overflow, false, `${locale}: no horizontal scroll`);
  }
});

test('F: every listed edition downloads anonymously as the exact published PDF in its advertised language', async () => {
  const links = new Map();
  for (const locale of LOCALES) for (const card of pages[locale].cards) {
    const book = bookOf(card);
    links.set(card.primaryHref, {book, locale: card.editionLocale});
    for (const other of card.others) links.set(other.href, {book, locale: other.locale});
  }
  assert.equal(links.size, inventory.books.reduce((n, b) => n + b.editions.length, 0), 'every edition is reachable, none invented');
  for (const [href, {book, locale}] of links) {
    const edition = book.editions.find(e => e.locale === locale);
    assert.ok(edition, `${book.originalTitle}: no ${locale} edition may be advertised`);
    const response = await fetch(BASE + href, {redirect: 'manual', signal: AbortSignal.timeout(180000)});
    const body = Buffer.from(await response.arrayBuffer());
    assert.equal(response.status, 200, href);
    assert.equal(response.headers.get('content-type'), 'application/pdf');
    assert.equal(body.subarray(0, 5).toString('latin1'), '%PDF-');
    assert.equal(body.length, edition.bytes, `${edition.file}: size`);
    assert.equal(createHash('sha256').update(body).digest('hex'), edition.sha256, `${edition.file}: SHA-256`);
  }
});
