import {formatDate, formatTime, localizeDigits, type Locale} from '@haramyar/i18n';
import type {BookingStatus} from './api.ts';
import {messages, type MessageKey} from './messages.ts';

type T = (key: MessageKey, params?: Record<string, string | number>) => string;

/** Label for a configurable code; unknown codes (added later by configuration) fall back to the code itself. */
export function codeLabel(t: T, prefix: 'category' | 'audience' | 'delivery', code?: string | null): string {
  if (!code) return '—';
  const key = `${prefix}_${code.replace(/-/g, '_')}` as MessageKey;
  return key in messages.fa ? t(key) : code;
}

export const statusLabel = (t: T, status: BookingStatus) => t(`status_${status}` as MessageKey);

export function when(locale: Locale, startsAt: string, endsAt: string, zone: string) {
  return {date: formatDate(startsAt, locale, zone), time: `${formatTime(startsAt, locale, zone)} – ${formatTime(endsAt, locale, zone)}`};
}

export const tracking = (code: string, locale: Locale) => localizeDigits(code, locale);
