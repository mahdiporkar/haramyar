/**
 * Pure rules of the request wizard (no React, no I/O) so they can be unit-tested with node --test. The backend stays
 * authoritative; these rules only give the user early, field-level feedback.
 */
import {compareDates, normalizeMobile, todayIn, toLatinDigits, zonedIso, type CivilDate} from '@haramyar/i18n';
import type {Catalog} from './api.ts';
import type {MessageKey} from './messages.ts';

export interface Draft {
  hostName: string; hostPhone: string; city: string; area: string; address: string; category: string; audience: string | null;
  capacity: string; notes: string; date: CivilDate | null; time: string; duration: number; zone: string;
  clientRequestId: string; step: number; savedAt: number;
}

export type Errors = Partial<Record<keyof Draft, MessageKey>>;

/** The wizard step on which each validated field is edited. */
const FIELD_STEP: Partial<Record<keyof Draft, 1 | 2 | 3>> = {hostName: 1, hostPhone: 1, city: 2, address: 2, capacity: 2, date: 3, time: 3};

/** Validates every step up to and including {@code step}. Times are compared as zoned instants, not calendar dates. */
export function validate(draft: Draft, step: number, now: Date = new Date()): Errors {
  const errors: Errors = {};
  if (step >= 1) {
    if (draft.hostName.trim().length < 2) errors.hostName = 'err_REQUIRED';
    if (!normalizeMobile(draft.hostPhone)) errors.hostPhone = 'err_PHONE';
  }
  if (step >= 2) {
    if (draft.city.trim().length < 2) errors.city = 'err_REQUIRED';
    if (draft.address.trim().length < 5) errors.address = 'err_ADDRESS';
    const capacity = toLatinDigits(draft.capacity).trim();
    if (capacity && (!/^\d+$/.test(capacity) || Number(capacity) < 1 || Number(capacity) > 5000)) errors.capacity = 'err_CAPACITY';
  }
  if (step >= 3) {
    const time = /^(\d{2}):(\d{2})$/.exec(draft.time);
    if (!draft.date || compareDates(draft.date, todayIn(draft.zone, now)) < 0) errors.date = 'err_DATE';
    if (!time || Number(time[1]) > 23 || Number(time[2]) > 59) errors.time = 'err_TIME';
    else if (draft.date && !errors.date && Date.parse(zonedIso(draft.date, Number(time[1]), Number(time[2]), draft.zone)) <= now.getTime())
      errors.time = 'err_TIME_PAST';
  }
  return errors;
}

/** The first step holding an invalid field, or null when there is none. */
export function firstInvalidStep(errors: Errors): 1 | 2 | 3 | null {
  const steps = (Object.keys(errors) as (keyof Draft)[]).map(field => FIELD_STEP[field] ?? 3);
  return steps.length ? (Math.min(...steps) as 1 | 2 | 3) : null;
}

/**
 * Catalog-backed choices for submission, or null when the catalog is missing or the draft holds a value the catalog does
 * not offer. Nothing outside the loaded catalog is ever submitted.
 */
export function catalogChoices(draft: Pick<Draft, 'category' | 'audience'>, catalog: Catalog | null):
    {category: string; audience: string | undefined; deliveryMode: string} | null {
  const deliveryMode = catalog?.deliveryModes[0];
  if (!catalog || !deliveryMode || !catalog.categories.includes(draft.category)) return null;
  if (draft.audience !== null && !catalog.audiences.includes(draft.audience)) return null;
  return {category: draft.category, audience: draft.audience ?? undefined, deliveryMode};
}

/** Aligns draft choices with a freshly loaded catalog (an unknown category becomes the first offered, an unknown audience none). */
export function alignWithCatalog<T extends Pick<Draft, 'category' | 'audience'>>(draft: T, catalog: Catalog): T {
  const category = catalog.categories.includes(draft.category) ? draft.category : catalog.categories[0] ?? draft.category;
  const audience = draft.audience !== null && !catalog.audiences.includes(draft.audience) ? null : draft.audience;
  return category === draft.category && audience === draft.audience ? draft : {...draft, category, audience};
}

/** Backend validation codes that belong to a single field of the wizard. */
export function fieldErrorFor(code: string): {field: keyof Draft; key: MessageKey; step: 1 | 2 | 3} | null {
  if (code === 'BOOKING_CEREMONY_TIME_NOT_IN_FUTURE') return {field: 'time', key: 'err_TIME_PAST', step: 3};
  return null;
}
