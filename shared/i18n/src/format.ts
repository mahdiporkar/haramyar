import type {Locale} from './locale.ts';
import {intlTag} from './locale.ts';

export function formatDate(instant: string | number | Date, locale: Locale, zone?: string, style: 'long' | 'medium' | 'short' = 'long'): string {
  const options: Intl.DateTimeFormatOptions = style === 'long'
    ? {weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'}
    : style === 'medium' ? {year: 'numeric', month: 'long', day: 'numeric'} : {month: 'long', day: 'numeric'};
  return new Intl.DateTimeFormat(intlTag(locale), {...options, ...(zone ? {timeZone: zone} : {})}).format(new Date(instant));
}

export function formatTime(instant: string | number | Date, locale: Locale, zone?: string): string {
  return new Intl.DateTimeFormat(intlTag(locale), {hour: '2-digit', minute: '2-digit', hourCycle: 'h23', ...(zone ? {timeZone: zone} : {})}).format(new Date(instant));
}

export function formatDateTime(instant: string | number | Date, locale: Locale, zone?: string): string {
  return `${formatDate(instant, locale, zone, 'medium')} · ${formatTime(instant, locale, zone)}`;
}

export function formatNumber(value: number, locale: Locale): string {
  return new Intl.NumberFormat(intlTag(locale)).format(value);
}

/** Localized digits for codes and numbers that must stay readable, e.g. tracking codes. */
export function localizeDigits(text: string, locale: Locale): string {
  if (locale === 'en') return text;
  const zero = locale === 'fa' ? 0x06f0 : 0x0660;
  return text.replace(/[0-9]/g, d => String.fromCharCode(zero + Number(d)));
}

/** Persian and Arabic-Indic digits to ASCII (for parsing user input). */
export function toLatinDigits(text: string): string {
  return text.replace(/[۰-۹]/g, d => String(d.charCodeAt(0) - 0x06f0)).replace(/[٠-٩]/g, d => String(d.charCodeAt(0) - 0x0660));
}

/**
 * Client-side mobile check for immediate feedback only (Iranian 09xx / +98 9xx, Iraqi +964 7xx). The server is
 * authoritative and normalizes with libphonenumber.
 */
export function normalizeMobile(raw: string): string | null {
  let value = toLatinDigits(raw).replace(/[\s\-()]/g, '');
  if (value.startsWith('00')) value = '+' + value.slice(2);
  if (/^09\d{9}$/.test(value)) return '+98' + value.slice(1);
  if (/^9\d{9}$/.test(value)) return '+98' + value;
  if (/^\+989\d{9}$/.test(value)) return value;
  if (/^\+9647\d{9}$/.test(value)) return value;
  return null;
}
