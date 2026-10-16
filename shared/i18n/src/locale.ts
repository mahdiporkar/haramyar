export type Locale = 'fa' | 'ar' | 'en';
export type Direction = 'rtl' | 'ltr';

export const LOCALES: readonly Locale[] = ['fa', 'ar', 'en'];
export const DEFAULT_LOCALE: Locale = 'fa';
export const LOCALE_STORAGE_KEY = 'haramyar.locale';
export const LOCALE_LABELS: Record<Locale, {short: string; native: string}> = {
  fa: {short: 'فا', native: 'فارسی'},
  ar: {short: 'ع', native: 'العربية'},
  en: {short: 'EN', native: 'English'},
};

export function isLocale(value: unknown): value is Locale {
  return value === 'fa' || value === 'ar' || value === 'en';
}

export function directionOf(locale: Locale): Direction {
  return locale === 'en' ? 'ltr' : 'rtl';
}

/** BCP 47 tag used for Intl formatting: Persian uses the Solar Hijri calendar, Arabic (Iraq) and English Gregorian. */
export function intlTag(locale: Locale): string {
  return locale === 'fa' ? 'fa-IR-u-ca-persian-nu-arabext' : locale === 'ar' ? 'ar-IQ-u-nu-arab' : 'en-GB';
}

/** Default ceremony time zone suggested for a locale (the user can change it). */
export function defaultZone(locale: Locale): string {
  return locale === 'ar' ? 'Asia/Baghdad' : 'Asia/Tehran';
}

/** Reads the stored preference; private browsing or blocked storage simply yields the default. */
export function storedLocale(): Locale {
  try {
    const value = globalThis.localStorage?.getItem(LOCALE_STORAGE_KEY);
    if (isLocale(value)) return value;
  } catch { /* storage unavailable */ }
  // Persian-first: Arabic browsers start in Arabic; English is used only when chosen explicitly.
  return globalThis.navigator?.language?.slice(0, 2) === 'ar' ? 'ar' : DEFAULT_LOCALE;
}

export function storeLocale(locale: Locale): void {
  try { globalThis.localStorage?.setItem(LOCALE_STORAGE_KEY, locale); } catch { /* storage unavailable */ }
}
