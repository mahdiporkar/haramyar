import {jalaliMonthLength, toGregorian, toJalali} from './jalali.ts';
import type {Locale} from './locale.ts';
import {intlTag} from './locale.ts';

/** A civil date (no time zone) expressed in the Gregorian calendar; the canonical form exchanged with backends. */
export interface CivilDate {year: number; month: number; day: number}

export interface CalendarDay {date: CivilDate; label: string; inMonth: boolean}
export interface CalendarMonth {title: string; weekdays: string[]; days: (CalendarDay | null)[]}

/** Month cursor in the user's display calendar: Jalali for Persian, Gregorian otherwise. */
export interface MonthCursor {year: number; month: number}

export function usesJalali(locale: Locale): boolean {
  return locale === 'fa';
}

export function cursorFor(date: CivilDate, locale: Locale): MonthCursor {
  if (!usesJalali(locale)) return {year: date.year, month: date.month};
  const j = toJalali(date.year, date.month, date.day);
  return {year: j.jy, month: j.jm};
}

export function shiftMonth(cursor: MonthCursor, delta: number): MonthCursor {
  const index = cursor.year * 12 + (cursor.month - 1) + delta;
  return {year: Math.floor(index / 12), month: (index % 12 + 12) % 12 + 1};
}

const JALALI_MONTHS: Record<Locale, string[]> = {
  fa: ['فروردین', 'اردیبهشت', 'خرداد', 'تیر', 'مرداد', 'شهریور', 'مهر', 'آبان', 'آذر', 'دی', 'بهمن', 'اسفند'],
  ar: ['فروردين', 'أرديبهشت', 'خرداد', 'تير', 'مرداد', 'شهريور', 'مهر', 'آبان', 'آذر', 'دي', 'بهمن', 'إسفند'],
  en: ['Farvardin', 'Ordibehesht', 'Khordad', 'Tir', 'Mordad', 'Shahrivar', 'Mehr', 'Aban', 'Azar', 'Dey', 'Bahman', 'Esfand'],
};
const WEEKDAYS: Record<Locale, string[]> = {
  fa: ['ش', 'ی', 'د', 'س', 'چ', 'پ', 'ج'],
  ar: ['س', 'ح', 'ن', 'ث', 'ر', 'خ', 'ج'],
  en: ['Sa', 'Su', 'Mo', 'Tu', 'We', 'Th', 'Fr'],
};

/** Saturday-first month grid in the locale's calendar, with localized digits. */
export function monthGrid(cursor: MonthCursor, locale: Locale): CalendarMonth {
  const digits = new Intl.NumberFormat(intlTag(locale), {useGrouping: false});
  const days: (CalendarDay | null)[] = [];
  let length: number;
  let first: CivilDate;
  let title: string;
  if (usesJalali(locale)) {
    length = jalaliMonthLength(cursor.year, cursor.month);
    const g = toGregorian(cursor.year, cursor.month, 1);
    first = {year: g.gy, month: g.gm, day: g.gd};
    title = `${JALALI_MONTHS[locale][cursor.month - 1]} ${digits.format(cursor.year)}`;
  } else {
    length = new Date(Date.UTC(cursor.year, cursor.month, 0)).getUTCDate();
    first = {year: cursor.year, month: cursor.month, day: 1};
    title = new Intl.DateTimeFormat(intlTag(locale), {month: 'long', year: 'numeric', timeZone: 'UTC'}).format(Date.UTC(cursor.year, cursor.month - 1, 1));
  }
  const firstWeekday = (new Date(Date.UTC(first.year, first.month - 1, first.day)).getUTCDay() + 1) % 7; // Saturday = 0
  for (let i = 0; i < firstWeekday; i++) days.push(null);
  for (let d = 0; d < length; d++) {
    const date = new Date(Date.UTC(first.year, first.month - 1, first.day + d));
    days.push({date: {year: date.getUTCFullYear(), month: date.getUTCMonth() + 1, day: date.getUTCDate()}, label: digits.format(d + 1), inMonth: true});
  }
  return {title, weekdays: WEEKDAYS[locale], days};
}

export function sameDate(a: CivilDate | null | undefined, b: CivilDate | null | undefined): boolean {
  return !!a && !!b && a.year === b.year && a.month === b.month && a.day === b.day;
}

export function compareDates(a: CivilDate, b: CivilDate): number {
  return (a.year - b.year) || (a.month - b.month) || (a.day - b.day);
}

/** Today's civil date in a time zone. */
export function todayIn(zone: string, now: Date = new Date()): CivilDate {
  const parts = new Intl.DateTimeFormat('en-CA', {timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit'}).formatToParts(now);
  const get = (type: string) => Number(parts.find(p => p.type === type)?.value);
  return {year: get('year'), month: get('month'), day: get('day')};
}

/** Offset of a zone at an instant, in minutes east of UTC. */
function offsetMinutes(zone: string, instant: number): number {
  const parts = new Intl.DateTimeFormat('en-US', {timeZone: zone, hourCycle: 'h23', year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: 'numeric', second: 'numeric'}).formatToParts(instant);
  const get = (type: string) => Number(parts.find(p => p.type === type)?.value);
  const asUtc = Date.UTC(get('year'), get('month') - 1, get('day'), get('hour') % 24, get('minute'), get('second'));
  return Math.round((asUtc - instant) / 60000);
}

/** ISO-8601 instant with offset for a wall-clock time in a zone, e.g. 2026-11-02T18:00:00+03:30. */
export function zonedIso(date: CivilDate, hour: number, minute: number, zone: string): string {
  const guess = Date.UTC(date.year, date.month - 1, date.day, hour, minute);
  let offset = offsetMinutes(zone, guess);
  offset = offsetMinutes(zone, guess - offset * 60000);
  const pad = (n: number) => String(Math.abs(n)).padStart(2, '0');
  const sign = offset >= 0 ? '+' : '-';
  return `${date.year}-${pad(date.month)}-${pad(date.day)}T${pad(hour)}:${pad(minute)}:00${sign}${pad(Math.trunc(offset / 60))}:${pad(offset % 60)}`;
}
