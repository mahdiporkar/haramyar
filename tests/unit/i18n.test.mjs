// Calendar and normalization helpers, cross-checked against the platform's Intl Persian calendar.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {register} from 'node:module';

register('data:text/javascript,' + encodeURIComponent(`
export async function load(url, context, next) {
  if (url.endsWith('.ts')) {
    const {readFileSync} = await import('node:fs');
    const {fileURLToPath} = await import('node:url');
    const {stripTypeScriptTypes} = await import('node:module');
    return {format: 'module', source: stripTypeScriptTypes(readFileSync(fileURLToPath(url), 'utf8')), shortCircuit: true};
  }
  return next(url, context);
}`));
const i18n = await import('../../shared/i18n/src/index.ts');

test('Jalali conversion matches Intl for every day across five years', () => {
  const fmt = new Intl.DateTimeFormat('en-u-ca-persian', {year: 'numeric', month: 'numeric', day: 'numeric', timeZone: 'UTC'});
  for (let t = Date.UTC(2023, 0, 1); t < Date.UTC(2028, 0, 1); t += 86400000) {
    const d = new Date(t);
    const parts = Object.fromEntries(fmt.formatToParts(d).map(p => [p.type, p.value]));
    const j = i18n.toJalali(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate());
    assert.deepEqual([j.jy, j.jm, j.jd], [Number(parts.year), Number(parts.month), Number(parts.day)], d.toISOString());
    const g = i18n.toGregorian(j.jy, j.jm, j.jd);
    assert.deepEqual([g.gy, g.gm, g.gd], [d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate()]);
  }
});

test('month grids are Saturday-first and use the locale calendar', () => {
  const farvardin = i18n.monthGrid({year: 1405, month: 1}, 'fa');
  assert.match(farvardin.title, /فروردین/);
  assert.equal(farvardin.days.filter(Boolean).length, 31);
  const first = farvardin.days.find(Boolean);
  assert.deepEqual(first.date, {year: 2026, month: 3, day: 21});
  const october = i18n.monthGrid({year: 2026, month: 10}, 'en');
  assert.equal(october.days.filter(Boolean).length, 31);
  assert.equal(october.days.findIndex(Boolean), 5, '1 October 2026 is a Thursday');
  assert.deepEqual(i18n.shiftMonth({year: 1404, month: 12}, 1), {year: 1405, month: 1});
});

test('zoned wall-clock times carry the correct offset', () => {
  assert.equal(i18n.zonedIso({year: 2026, month: 11, day: 2}, 18, 0, 'Asia/Tehran'), '2026-11-02T18:00:00+03:30');
  assert.equal(i18n.zonedIso({year: 2026, month: 11, day: 2}, 9, 5, 'Asia/Baghdad'), '2026-11-02T09:05:00+03:00');
});

test('mobile hints and digit handling', () => {
  for (const raw of ['09121234567', '۰۹۱۲ ۱۲۳ ۴۵۶۷', '+98 912 123 4567', '00989121234567']) assert.equal(i18n.normalizeMobile(raw), '+989121234567', raw);
  assert.equal(i18n.normalizeMobile('+964 770 123 4567'), '+9647701234567');
  assert.equal(i18n.normalizeMobile('12345'), null);
  assert.equal(i18n.localizeDigits('RZ-7K3M-Q9TD', 'fa'), 'RZ-۷K۳M-Q۹TD');
  assert.equal(i18n.toLatinDigits('١٢٣۴۵'), '12345');
});

test('dictionaries define every key in every language', () => {
  const keys = Object.keys(i18n.common.fa).sort();
  for (const locale of ['ar', 'en']) assert.deepEqual(Object.keys(i18n.common[locale]).sort(), keys, locale);
  const t = i18n.translator(i18n.common, 'en');
  assert.equal(t('home'), 'Home');
  assert.equal(i18n.directionOf('ar'), 'rtl');
  assert.equal(i18n.directionOf('en'), 'ltr');
});
