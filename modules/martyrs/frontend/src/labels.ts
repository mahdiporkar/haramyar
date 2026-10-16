import {formatNumber, type Locale} from '@haramyar/i18n';
import type {Calendar, LifeEvent} from './api.ts';
import type {MessageKey} from './messages.ts';

type T = (key: MessageKey, params?: Record<string, string | number>) => string;

/** Historical dates are shown exactly as recorded (calendar named), never converted between calendars. */
export function yearLabel(t: T, locale: Locale, year?: number, calendar?: Calendar): string | null {
  if (!year || !calendar) return null;
  return `${new Intl.NumberFormat(locale === 'en' ? 'en' : locale === 'fa' ? 'fa-IR' : 'ar-IQ', {useGrouping: false}).format(year)} ${t(`cal_${calendar}` as MessageKey)}`;
}

export function lifeEventLabel(t: T, locale: Locale, event?: LifeEvent): string | null {
  if (!event) return null;
  const date = event.date;
  const parts: string[] = [];
  if (date) {
    const digits = (n: number) => new Intl.NumberFormat(locale === 'en' ? 'en' : locale === 'fa' ? 'fa-IR' : 'ar-IQ', {useGrouping: false}).format(n);
    parts.push([date.day, date.month, date.year].filter((n): n is number => typeof n === 'number').map(digits).join('/') + ' ' + t(`cal_${date.calendar}` as MessageKey));
  }
  if (event.place) parts.push(event.place);
  return parts.length ? parts.join(' · ') : null;
}

export const count = (locale: Locale, n: number) => formatNumber(n, locale);

/** Plain-text paragraphs (blank-line separated); text is rendered as text, never as HTML. */
export const paragraphs = (text?: string) => (text ?? '').split(/\n\s*\n/).map(p => p.trim()).filter(Boolean);
