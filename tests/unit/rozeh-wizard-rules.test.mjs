// Pure rules of the Rozeh request wizard: field validation with zoned times, the first invalid step, the catalog guard
// and the mapping of backend field errors.
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

const {validate, firstInvalidStep, catalogChoices, alignWithCatalog, fieldErrorFor} = await import('../../modules/rozeh-booking/frontend/src/wizard-rules.ts');

// 2026-10-29 14:30 in Tehran (UTC+03:30) and Baghdad (UTC+03:00).
const NOW = new Date('2026-10-29T11:00:00Z');
const TODAY = {year: 2026, month: 10, day: 29};
const valid = (overrides = {}) => ({
  hostName: 'میزبان', hostPhone: '۰۹۱۲۱۲۳۴۵۶۷', city: 'قم', area: '', address: 'خیابان آزمایشی ۷', category: 'rozeh', audience: 'family',
  capacity: '', notes: '', date: {year: 2026, month: 11, day: 5}, time: '18:00', duration: 60, zone: 'Asia/Tehran',
  clientRequestId: 'c1', step: 4, savedAt: 0, ...overrides,
});
const CATALOG = {categories: ['rozeh'], audiences: ['family', 'women', 'men'], deliveryModes: ['in-person'], confirmationBy: ['HOST'],
  providerRequiredForConfirmation: false, hostCancellationNoticeMinutes: 0, requireVerifiedMobile: false, provisional: true};

test('a complete draft is valid', () => {
  assert.deepEqual(validate(valid(), 3, NOW), {});
});

test('a time already elapsed today is rejected on the time field (zoned comparison, not date-only)', () => {
  assert.deepEqual(validate(valid({date: TODAY, time: '14:00'}), 3, NOW), {time: 'err_TIME_PAST'});
  assert.deepEqual(validate(valid({date: TODAY, time: '14:30'}), 3, NOW), {time: 'err_TIME_PAST'}, 'the current minute is not in the future');
  assert.deepEqual(validate(valid({date: TODAY, time: '14:31'}), 3, NOW), {});
  // 14:15 is past in Tehran (14:30) but still ahead in Baghdad (14:00).
  assert.deepEqual(validate(valid({date: TODAY, time: '14:15', zone: 'Asia/Baghdad'}), 3, NOW), {});
  assert.deepEqual(validate(valid({date: TODAY, time: '14:15', zone: 'Asia/Tehran'}), 3, NOW), {time: 'err_TIME_PAST'});
});

test('past dates and malformed times are reported on their own fields', () => {
  assert.deepEqual(validate(valid({date: {year: 2026, month: 10, day: 28}}), 3, NOW), {date: 'err_DATE'});
  assert.deepEqual(validate(valid({date: null}), 3, NOW), {date: 'err_DATE'});
  assert.deepEqual(validate(valid({time: '25:00'}), 3, NOW), {time: 'err_TIME'});
  assert.deepEqual(validate(valid({time: ''}), 3, NOW), {time: 'err_TIME'});
});

test('the first invalid step is chosen and every error is kept', () => {
  const errors = validate(valid({hostName: '', address: 'x', date: null}), 3, NOW);
  assert.deepEqual(errors, {hostName: 'err_REQUIRED', address: 'err_ADDRESS', date: 'err_DATE'});
  assert.equal(firstInvalidStep(errors), 1);
  assert.equal(firstInvalidStep({capacity: 'err_CAPACITY', time: 'err_TIME'}), 2);
  assert.equal(firstInvalidStep({time: 'err_TIME_PAST'}), 3);
  assert.equal(firstInvalidStep({}), null);
});

test('validation only covers the steps reached so far', () => {
  assert.deepEqual(validate(valid({city: '', date: null}), 1, NOW), {});
  assert.deepEqual(validate(valid({city: '', date: null}), 2, NOW), {city: 'err_REQUIRED'});
});

test('submission values come only from the loaded catalog', () => {
  assert.equal(catalogChoices(valid(), null), null, 'no catalog, no submission');
  assert.equal(catalogChoices(valid(), {...CATALOG, deliveryModes: []}), null, 'no delivery mode offered');
  assert.equal(catalogChoices(valid({category: 'maddahi'}), CATALOG), null, 'category not offered');
  assert.equal(catalogChoices(valid({audience: 'children'}), CATALOG), null, 'audience not offered');
  assert.deepEqual(catalogChoices(valid(), CATALOG), {category: 'rozeh', audience: 'family', deliveryMode: 'in-person'});
  assert.deepEqual(catalogChoices(valid({audience: null}), CATALOG), {category: 'rozeh', audience: undefined, deliveryMode: 'in-person'});
});

test('a loaded catalog replaces choices it does not offer', () => {
  const draft = valid({category: 'rozeh', audience: 'children'});
  const aligned = alignWithCatalog(draft, {...CATALOG, categories: ['sokhanrani']});
  assert.equal(aligned.category, 'sokhanrani');
  assert.equal(aligned.audience, null);
  const same = valid();
  assert.equal(alignWithCatalog(same, CATALOG), same, 'unchanged drafts keep their identity');
});

test('the backend future-time refusal maps to the time field on step 3', () => {
  assert.deepEqual(fieldErrorFor('BOOKING_CEREMONY_TIME_NOT_IN_FUTURE'), {field: 'time', key: 'err_TIME_PAST', step: 3});
  assert.equal(fieldErrorFor('BOOKING_INVALID_VALUE'), null);
});
