import type {Locale} from './locale.ts';

/** A dictionary whose Persian entries define the keys; Arabic and English must provide the same keys (type-checked). */
export type Dictionary<K extends string> = Record<Locale, Record<K, string>>;

/** Returns a translator with Persian fallback and `{name}` placeholders. */
export function translator<K extends string>(dictionary: Dictionary<K>, locale: Locale) {
  const table = dictionary[locale] ?? dictionary.fa;
  return (key: K, params?: Record<string, string | number>): string => {
    let text = table[key] ?? dictionary.fa[key] ?? key;
    if (params) for (const [name, value] of Object.entries(params)) text = text.split(`{${name}}`).join(String(value));
    return text;
  };
}
