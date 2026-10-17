/**
 * HaramYar public site: server-rendered, crawlable pages for published martyr profiles. Data comes only from Hive
 * PUBLIC route operations through the BFF (drafts are structurally unreachable there); no session, cookies or tokens
 * are involved. Canonical URLs: /martyr/{slug}?lang=fa|ar|en; the interactive app lives at /martyrs/profiles/{slug}.
 */
import {createServer, type IncomingMessage, type ServerResponse} from 'node:http';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';
import {renderToStaticMarkup} from 'react-dom/server';
import {directionOf, isLocale, type Locale} from '@haramyar/i18n';

const PORT = Number(process.env.PORT ?? 8093);
const BFF = (process.env.HARAMYAR_BFF_URL ?? 'http://localhost:8080').replace(/\/+$/, '');
const ORIGIN = (process.env.HARAMYAR_PUBLIC_ORIGIN ?? 'http://localhost:8080').replace(/\/+$/, '');
interface Cover {sourceId: string; bookId: string; bookTitle: string; matchStatus: string; width?: number; height?: number; web180?: string; web360?: string}
const ASSET_ROOT = fileURLToPath(new URL('.', import.meta.url));
const COVERS: Cover[] = JSON.parse(readFileSync(join(ASSET_ROOT, 'covers.json'), 'utf8'));
const COVER_FILES = new Set(COVERS.flatMap(c => [c.web180, c.web360].filter((f): f is string => !!f)));
const CLIENT = readFileSync(join(ASSET_ROOT, 'profile.js'));
const CSS = readFileSync(join(fileURLToPath(new URL('.', import.meta.url)), 'site.css'));

type Calendar = 'SOLAR_HIJRI' | 'LUNAR_HIJRI' | 'GREGORIAN';
interface LifeEvent {date?: {year: number; month?: number; day?: number; calendar: Calendar}; place?: string}
interface Media {mediaId: string; caption?: string; credit?: string}
interface Source {id: string; kind: string; title: string; author?: string; publisher?: string; year?: number; url?: string; note?: string}
interface Profile {
  slug: string; fullName: string; honorific?: string; contentLocale: string; sourceLocale: string; shortDescription?: string; biography?: string; birth?: LifeEvent; martyrdom?: LifeEvent;
  portrait?: Media; gallery: Media[]; sources: Source[]; stories: {id: string; title: string; body: string}[]; seo?: {title?: string; description?: string}; firstPublishedAt: string; updatedAt: string;
}
interface Summary {slug: string; fullName: string; honorific?: string; shortDescription?: string; portraitMediaId?: string}
/** selectedEdition is chosen by the martyrs service (requested language, else Arabic) among published editions with a PDF. */
interface Book {id: string; kind: 'AUTHORED' | 'ABOUT'; originalTitle: string; sourceUrl: string; selectedEdition: {id: string; title: string; summary?: string; locale: Locale};
  availableEditions: {id: string; title: string; locale: Locale}[]}
const LANGUAGE_NAMES: Record<Locale, string> = {fa: 'فارسی', ar: 'العربية', en: 'English'};

const TEXT = {
  fa: {site: 'حرم‌یار', archive: 'معرفی شهدا', born: 'تولد', martyred: 'شهادت', biography: 'زندگی‌نامه', stories: 'خاطرات و روایت‌ها', sources: 'منابع و مراجع', openApp: 'مشاهده در اپلیکیشن حرم‌یار', notFound: 'این پرونده در دسترس نیست', all: 'همه پرونده‌ها', empty: 'هنوز پرونده‌ای منتشر نشده است', note: 'محتوای حرم‌یار تنها پس از بررسی منبع و تأیید تحریریه منتشر می‌شود.', language: 'زبان نمایش', sourceLanguage: 'متن زندگی‌نامه هنوز فقط به زبان اصلی ({language}) منتشر شده است.', bookFallback: 'ترجمهٔ این اثر هنوز به زبان انتخاب‌شده منتشر نشده است؛ عنوان زبان موجود نمایش داده می‌شود.', bookFallbackArabic: 'ترجمهٔ فارسی این کتاب هنوز موجود نیست؛ نسخهٔ اصلی عربی در دسترس است.', library: 'کتابخانه آثار', download: 'دانلود نسخهٔ {language}', otherEditions: 'نسخه‌های دیگر', editionLanguage: 'زبان نسخه', languages: {fa: 'فارسی', ar: 'عربی', en: 'انگلیسی'}, cal: {SOLAR_HIJRI: 'ه‍.ش', LUNAR_HIJRI: 'ه‍.ق', GREGORIAN: 'م'}},
  ar: {site: 'حرم‌يار', archive: 'التعريف بالشهداء', born: 'الولادة', martyred: 'الشهادة', biography: 'السيرة', stories: 'الذكريات والروايات', sources: 'المصادر والمراجع', openApp: 'العرض في تطبيق حرم‌يار', notFound: 'هذا الملف غير متاح', all: 'كل الملفات', empty: 'لم يُنشر أي ملف بعد', note: 'لا يُنشر محتوى حرم‌يار إلا بعد التحقق من المصدر واعتماد التحرير.', language: 'لغة العرض', sourceLanguage: 'السيرة متاحة حالياً بلغتها الأصلية فقط ({language}).', bookFallback: 'ترجمة هذا الكتاب غير منشورة باللغة المختارة؛ يظهر عنوان النسخة المتاحة.', bookFallbackArabic: 'ترجمة هذا الكتاب غير منشورة باللغة المختارة؛ يظهر عنوان النسخة المتاحة.', library: 'مكتبة المؤلفات', download: 'تحميل النسخة {language}', otherEditions: 'نسخ أخرى', editionLanguage: 'لغة النسخة', languages: {fa: 'الفارسية', ar: 'العربية', en: 'الإنجليزية'}, cal: {SOLAR_HIJRI: 'هـ.ش', LUNAR_HIJRI: 'هـ', GREGORIAN: 'م'}},
  en: {site: 'Haramyar', archive: 'Martyrs archive', born: 'Born', martyred: 'Martyred', biography: 'Biography', stories: 'Memories and stories', sources: 'Sources and references', openApp: 'Open in the Haramyar app', notFound: 'This profile is not available', all: 'All profiles', empty: 'No profile has been published yet', note: 'Haramyar publishes content only after source review and editorial approval.', language: 'Display language', sourceLanguage: 'The biography is currently published only in its source language ({language}).', bookFallback: 'A translation into the selected language has not been published; the available edition is shown.', bookFallbackArabic: 'An English translation is not yet available. The original Arabic edition is available.', library: 'Library of works', download: 'Download {language} PDF', otherEditions: 'Other editions', editionLanguage: 'Edition language', languages: {fa: 'Persian', ar: 'Arabic', en: 'English'}, cal: {SOLAR_HIJRI: 'SH', LUNAR_HIJRI: 'AH', GREGORIAN: 'CE'}},
} as const;

async function bff<T>(path: string): Promise<{status: number; body: T | null}> {
  const response = await fetch(`${BFF}/api/routes/martyrs${path}`, {headers: {Accept: 'application/json', 'X-Correlation-Id': crypto.randomUUID()}, redirect: 'manual', signal: AbortSignal.timeout(8000)});
  return {status: response.status, body: response.ok ? await response.json() as T : null};
}

const escapeXml = (value: string) => value.replace(/[<>&'"]/g, c => ({'<': '&lt;', '>': '&gt;', '&': '&amp;', "'": '&apos;', '"': '&quot;'})[c]!);
const paragraphs = (text?: string) => (text ?? '').split(/\n\s*\n/).map(p => p.trim()).filter(Boolean);
const sourceHost = (url: string) => { try { return new URL(url).hostname; } catch { return url; } };
const lifeEvent = (event: LifeEvent | undefined, locale: Locale) => {
  if (!event) return null;
  const d = event.date;
  return [d ? `${[d.day, d.month, d.year].filter(Boolean).join('/')} ${TEXT[locale].cal[d.calendar]}` : null, event.place].filter(Boolean).join(' · ') || null;
};

const PROFILE_LOCALES: Locale[] = ['fa', 'ar', 'en'];
const profileUrl = (slug: string, locale: Locale) => `/martyr/${encodeURIComponent(slug)}?lang=${locale}`;

function Document({locale, title, description, canonical, alternateSlug, image, jsonLd, robots, children}: {locale: Locale; title: string; description?: string; canonical?: string; alternateSlug?: string; image?: string; jsonLd?: object; robots?: string; children: React.ReactNode}) {
  return (
    <html lang={locale} dir={directionOf(locale)}>
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <title>{title}</title>
        {description && <meta name="description" content={description} />}
        {robots && <meta name="robots" content={robots} />}
        {canonical && <link rel="canonical" href={canonical} />}
        {alternateSlug && PROFILE_LOCALES.map(lang => <link key={lang} rel="alternate" hrefLang={lang} href={`${ORIGIN}${profileUrl(alternateSlug, lang)}`} />)}
        <meta property="og:site_name" content={TEXT[locale].site} />
        <meta property="og:title" content={title} />
        {description && <meta property="og:description" content={description} />}
        {canonical && <meta property="og:url" content={canonical} />}
        <meta property="og:type" content={jsonLd ? 'profile' : 'website'} />
        <meta property="og:locale" content={locale === 'fa' ? 'fa_IR' : locale === 'ar' ? 'ar_IQ' : 'en_US'} />
        {image && <meta property="og:image" content={image} />}
        <meta name="twitter:card" content={image ? 'summary_large_image' : 'summary'} />
        <link rel="icon" type="image/png" href="/assets/brand/haramyar-app-icon-192.png" />
        <link rel="stylesheet" href="/martyr/_assets/site.css?v=memorial-1" />
        {alternateSlug && <script defer src="/martyr/_assets/profile.js?v=memorial-1" />}
        {jsonLd && <script type="application/ld+json" dangerouslySetInnerHTML={{__html: JSON.stringify(jsonLd).replace(/</g, '\\u003c')}} />}
      </head>
      <body className={alternateSlug ? "memorial-document" : undefined}>
        <div className="hy hy-tokens" data-theme="default" dir={directionOf(locale)} lang={locale}>
          <header className="public-header"><a href="/"><img src="/assets/brand/haramyar-header-logo.svg" alt="" /> {TEXT[locale].site}</a><a href="/martyr">{TEXT[locale].archive}</a></header>
          <a className="skip-link" href="#main-content">{locale === 'fa' ? 'رفتن به محتوا' : locale === 'ar' ? 'انتقل إلى المحتوى' : 'Skip to content'}</a>
          <main id="main-content" className="public-main">{children}</main>
          <footer className="public-footer">{TEXT[locale].note} · <a href="/">haramyar</a></footer>
        </div>
      </body>
    </html>
  );
}

const bookFile = (editionId: string) => `/api/routes/martyrs/public/books/${editionId}/file`;

/** One work: the edition the service selected for this language is primary; a fallback is always labelled as such. */
function BookCard({book, locale, slug}: {book: Book; locale: Locale; slug: string}) {
  const text = TEXT[locale];
  const selected = book.selectedEdition;
  const fallback = selected.locale !== locale;
  const download = (edition: Locale) => text.download.replace('{language}', text.languages[edition]);
  const others = book.availableEditions.filter(e => e.id !== selected.id);
  const cover = slug === 'izz-al-din-salim' ? COVERS.find(c => c.bookTitle === book.originalTitle) : undefined;
  const coverSet = cover?.web360 ? [
    ...(cover.width! > 180 ? [`/martyr/_assets/covers/${cover.web180} 180w`] : []),
    `/martyr/_assets/covers/${cover.web360} ${Math.min(360, cover.width!)}w`,
  ].join(', ') : undefined;
  const labels = MEMORIAL[locale];
  return (
    <li className="book-card" data-book-id={book.id} data-edition-id={selected.id} data-edition-locale={selected.locale} data-fallback={fallback} data-search={[book.originalTitle, ...book.availableEditions.map(e => e.title)].join(" ")} data-available={book.availableEditions.map(e => e.locale).join(" ")}>
      <figure className="book-art" data-cover-status={cover?.matchStatus ?? "missing"}>
        <div className="cover-placeholder" hidden={!!cover?.web180}><span aria-hidden="true">✦</span><span lang="ar" dir="rtl">{book.originalTitle}</span><small>{labels.noCover}</small></div>
        {cover?.web180 && <img className="book-cover-image" src={`/martyr/_assets/covers/${cover.web180}`} srcSet={coverSet} sizes="(max-width: 700px) 100px, (max-width: 950px) 105px, (min-width: 1500px) 110px, 140px" width={cover.width} height={cover.height} alt={`${labels.originalCover}: ${book.originalTitle}`} loading="lazy" decoding="async" />}
        <figcaption>{cover?.web180 ? labels.originalCover : labels.noCover}</figcaption>
      </figure>
      <div className="book-info">
      <h3 lang={selected.locale} dir={directionOf(selected.locale)}>{selected.title}</h3>
      <span className="edition-language">{text.editionLanguage}: <span lang={locale}>{text.languages[selected.locale]}</span></span>
      {fallback && <p className="book-fallback" role="note">{selected.locale === 'ar' ? text.bookFallbackArabic : text.bookFallback}</p>}
      {selected.summary && <p lang={selected.locale} dir={directionOf(selected.locale)}>{selected.summary}</p>}
      <div className="book-actions">
        <a className="btn btn-primary book-download" data-primary="true" href={bookFile(selected.id)} hrefLang={selected.locale} type="application/pdf"
          target="_blank" rel="noopener noreferrer">{download(selected.locale)}</a>
        {others.length > 0 && <div className="book-editions"><span>{text.otherEditions}:</span>
          {others.map(e => <a key={e.id} href={bookFile(e.id)} hrefLang={e.locale} lang={e.locale} type="application/pdf" target="_blank" rel="noopener noreferrer"
            data-edition-locale={e.locale} aria-label={download(e.locale)}>{LANGUAGE_NAMES[e.locale]}</a>)}
        </div>}
      </div>
      </div>
    </li>
  );
}

const MEMORIAL = {
  fa: {eyebrow: 'یادمان زندگی و اندیشه', intro: 'روایتی از زندگی؛ میراثی برای خواندن', libraryIntro: 'در میان آثار قدم بزنید؛ هر کتاب، دریچه‌ای به یک اندیشه.', read: 'خواندن زندگی‌نامه', explore: 'کاوش در کتابخانه', works: 'عنوان کتاب', editions: 'نسخهٔ منتشرشده', languages: 'زبان', search: 'جست‌وجو در عنوان کتاب‌ها', translated: 'فقط نسخه‌های فارسی', results: 'کتاب', noResults: 'کتابی با این جست‌وجو پیدا نشد.', originalCover: 'جلد نسخهٔ اصلی عربی', noCover: 'تصویر جلد در دسترس نیست', heritage: 'زندگی، اندیشه، آثار', sourcesIntro: 'برای مطالعه و بررسی بیشتر', back: 'بازگشت به بالا'},
  ar: {eyebrow: 'ذاكرة الحياة والفكر', intro: 'سيرة تُروى؛ وإرث يُقرأ', libraryIntro: 'تصفّح المؤلفات؛ في كل كتاب نافذة على فكرة.', read: 'قراءة السيرة', explore: 'استكشاف المكتبة', works: 'عنواناً', editions: 'نسخة منشورة', languages: 'لغات', search: 'البحث في عناوين الكتب', translated: 'النسخ العربية فقط', results: 'كتاباً', noResults: 'لم يُعثر على كتب مطابقة.', originalCover: 'غلاف النسخة العربية الأصلية', noCover: 'صورة الغلاف غير متاحة', heritage: 'حياة وفكر ومؤلفات', sourcesIntro: 'للقراءة والبحث', back: 'العودة إلى الأعلى'},
  en: {eyebrow: 'A life remembered. A legacy preserved.', intro: 'A story to remember. A legacy to read.', libraryIntro: 'Explore the collected works. Every book opens a window onto an idea.', read: 'Read the biography', explore: 'Explore the library', works: 'Book titles', editions: 'Published editions', languages: 'Languages', search: 'Search book titles', translated: 'English editions only', results: 'books', noResults: 'No books match your search.', originalCover: 'Original Arabic cover', noCover: 'Cover image unavailable', heritage: 'Life, thought & collected works', sourcesIntro: 'For further reading and research', back: 'Back to top'},
} as const;

function ProfilePage({profile, books, locale}: {profile: Profile; books: Book[]; locale: Locale}) {
  const text = TEXT[locale], labels = MEMORIAL[locale];
  const sourceLocale: Locale = isLocale(profile.contentLocale) ? profile.contentLocale : 'fa';
  const born = lifeEvent(profile.birth, locale), died = lifeEvent(profile.martyrdom, locale);
  const number = (n: number) => new Intl.NumberFormat(locale).format(n);
  const editions = books.reduce((sum, b) => sum + b.availableEditions.length, 0);
  const languages = new Set(books.flatMap(b => b.availableEditions.map(e => e.locale))).size;
  return (
    <div className="memorial" id="memorial-top">
      <section className="memorial-hero" aria-labelledby="memorial-name">
        <div className="hero-geometry" aria-hidden="true" />
        <div className="hero-topline"><a href="/martyr">{text.archive}</a>
          <nav className="profile-languages" aria-label={text.language}>
            {PROFILE_LOCALES.map(lang => <a key={lang} href={profileUrl(profile.slug, lang)} lang={lang} hrefLang={lang} aria-current={locale === lang ? 'page' : undefined}>{LANGUAGE_NAMES[lang]}</a>)}
          </nav>
        </div>
        <div className="hero-inner">
          <div className="hero-copy">
            <p className="eyebrow"><span aria-hidden="true">✦</span> {labels.eyebrow}</p>
            {profile.honorific && <span className="hero-honorific">{profile.honorific}</span>}
            <h1 id="memorial-name" lang={sourceLocale} dir={directionOf(sourceLocale)}>{profile.fullName}</h1>
            {profile.shortDescription && <p className="hero-intro" lang={sourceLocale} dir={directionOf(sourceLocale)}>{profile.shortDescription}</p>}
            <div className="hero-actions"><a className="memorial-button gold" href="#biography">{labels.read}<span aria-hidden="true">↗</span></a>{books.length > 0 && <a className="memorial-button outline" href="#library">{labels.explore}<span aria-hidden="true">↓</span></a>}</div>
          </div>
          <figure className="hero-portrait">
            <div className="portrait-arch">{profile.portrait ? <img src={`/api/routes/martyrs/public/media/${profile.portrait.mediaId}`} alt={profile.portrait.caption ?? profile.fullName} width="420" height="480" fetchPriority="high" /> : <span className="portrait-empty">{profile.fullName}</span>}</div>
            <figcaption>{profile.portrait?.credit ?? labels.heritage}</figcaption>
          </figure>
        </div>
        <div className="hero-bottom"><span>{labels.heritage}</span><span className="hero-line" /><span aria-hidden="true">✦</span></div>
      </section>
      <nav className="memorial-nav" aria-label={labels.heritage}><a href="#biography">{text.biography}</a>{books.length > 0 && <a href="#library">{text.library}<span>{number(books.length)}</span></a>}<a href="#references">{text.sources}</a></nav>
      <article className="memorial-content" itemScope itemType="https://schema.org/Person">
        <meta itemProp="name" content={profile.fullName} />
        <section className="biography-section" id="biography" aria-labelledby="biography-heading">
          <aside className="biography-aside"><span className="section-number" aria-hidden="true">01 /</span><h2 id="biography-heading">{text.biography}</h2><p className="section-caption">{labels.intro}</p>
            {(born || died) && <dl className="life-timeline">{born && <div><dt>{text.born}</dt><dd>{born}</dd></div>}{died && <div><dt>{text.martyred}</dt><dd>{died}</dd></div>}</dl>}
            <a className="app-link" href={`/martyrs/profiles/${encodeURIComponent(profile.slug)}`}>{text.openApp} <span aria-hidden="true">↗</span></a>
          </aside>
          <div className="biography-prose">
            {sourceLocale !== locale && <p className="translation-notice" role="status">{text.sourceLanguage.replace('{language}', LANGUAGE_NAMES[sourceLocale])}</p>}
            <div itemProp="description" lang={sourceLocale} dir={directionOf(sourceLocale)}>{paragraphs(profile.biography).map((p, i) => <p key={i}>{p}</p>)}</div>
            {profile.stories.length > 0 && <section><h3>{text.stories}</h3>{profile.stories.map(s => <section key={s.id} lang={isLocale(profile.sourceLocale) ? profile.sourceLocale : sourceLocale} dir={directionOf(isLocale(profile.sourceLocale) ? profile.sourceLocale : sourceLocale)}><h4>{s.title}</h4>{paragraphs(s.body).map((p, i) => <p key={i}>{p}</p>)}</section>)}</section>}
          </div>
        </section>
        {books.length > 0 && <section className="books-section" id="library" aria-labelledby="library-heading">
          <div className="library-heading"><div><span className="section-number" aria-hidden="true">02 /</span><h2 id="library-heading">{text.library}</h2><p>{labels.libraryIntro}</p></div><div className="library-stats"><div><b>{number(books.length)}</b><span>{labels.works}</span></div><div><b>{number(editions)}</b><span>{labels.editions}</span></div><div><b>{number(languages)}</b><span>{labels.languages}</span></div></div></div>
          <div className="library-controls" hidden data-library-controls>
            <label className="library-search"><span>{labels.search}</span><input type="search" id="library-search" placeholder={labels.search} autoComplete="off" aria-controls="book-list" /></label>
            {locale !== 'ar' && <label className="translation-filter"><input type="checkbox" id="translated-only" />{labels.translated}</label>}
            <output id="library-count" aria-live="polite" data-result-label={labels.results}>{number(books.length)} {labels.results}</output>
          </div>
          <p className="no-results" id="no-results" hidden>{labels.noResults}</p>
          <ul className="books-list" id="book-list">{books.map(book => <BookCard key={book.id} book={book} locale={locale} slug={profile.slug} />)}</ul>
        </section>}
        <section className="reference-section" id="references" aria-labelledby="references-heading"><div><span className="section-number" aria-hidden="true">03 /</span><h2 id="references-heading">{text.sources}</h2><p>{labels.sourcesIntro}</p></div>
          <ol className="sources">{profile.sources.map(s => <li key={s.id}><cite>{s.title}</cite>{[s.author, s.publisher, s.year].filter(Boolean).length > 0 && <span>{[s.author, s.publisher, s.year].filter(Boolean).join(' · ')}</span>}{s.url && <a href={s.url} rel="nofollow noopener noreferrer" aria-label={`${s.title} — ${sourceHost(s.url)}`}>{sourceHost(s.url)}<span aria-hidden="true"> ↗</span></a>}</li>)}</ol>
        </section>
        <a className="back-top" href="#memorial-top">{labels.back} ↑</a>
      </article>
    </div>
  );
}

function send(res: ServerResponse, status: number, type: string, body: string | Buffer, cache: string) {
  res.writeHead(status, {'Content-Type': type, 'Cache-Control': cache, 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'strict-origin-when-cross-origin'});
  res.end(body);
}

function html(element: React.ReactElement) {
  return '<!doctype html>' + renderToStaticMarkup(element);
}

async function handle(req: IncomingMessage, res: ServerResponse) {
  const url = new URL(req.url ?? '/', 'http://local');
  const path = url.pathname;
  if (req.method !== 'GET' && req.method !== 'HEAD') return send(res, 405, 'text/plain', 'Method not allowed', 'no-store');
  if (path === '/healthz') return send(res, 200, 'text/plain', 'ok', 'no-store');
  if (path === '/martyr/_assets/site.css') return send(res, 200, 'text/css; charset=utf-8', CSS, 'public, max-age=3600');
  if (path === '/martyr/_assets/profile.js') return send(res, 200, 'text/javascript; charset=utf-8', CLIENT, 'public, max-age=3600');
  const coverFile = path.startsWith('/martyr/_assets/covers/') ? path.slice('/martyr/_assets/covers/'.length) : '';
  if (coverFile && COVER_FILES.has(coverFile)) return send(res, 200, 'image/webp', readFileSync(join(ASSET_ROOT, 'covers', coverFile)), 'public, max-age=31536000, immutable');
  if (path === '/robots.txt') {
    return send(res, 200, 'text/plain; charset=utf-8', `User-agent: *\nAllow: /\nDisallow: /api/\nDisallow: /auth/\nDisallow: /admin/\nDisallow: /realms/\nDisallow: /console/\nSitemap: ${ORIGIN}/sitemap.xml\n`, 'public, max-age=3600');
  }
  if (path === '/sitemap.xml') {
    const {body} = await bff<{slug: string; lastModified: string}[]>('/public/sitemap');
    const urls = [{loc: `${ORIGIN}/`, lastmod: undefined}, {loc: `${ORIGIN}/martyr`, lastmod: undefined},
      ...(body ?? []).map(e => ({loc: `${ORIGIN}/martyr/${encodeURIComponent(e.slug)}`, lastmod: e.lastModified}))];
    const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls.map(u => `  <url><loc>${escapeXml(u.loc)}</loc>${u.lastmod ? `<lastmod>${escapeXml(u.lastmod)}</lastmod>` : ''}</url>`).join('\n')}\n</urlset>\n`;
    return send(res, 200, 'application/xml; charset=utf-8', xml, 'public, max-age=600');
  }
  if (path === '/martyr' || path === '/martyr/') {
    const page = Math.max(0, Number(url.searchParams.get('page') ?? 0) || 0);
    const {body} = await bff<{items: Summary[]; total: number; size: number}>(`/public/profiles?page=${page}&size=30`);
    const locale: Locale = 'fa';
    return send(res, 200, 'text/html; charset=utf-8', html(
      <Document locale={locale} title={`${TEXT[locale].archive} | ${TEXT[locale].site}`} description={TEXT[locale].note} canonical={`${ORIGIN}/martyr${page ? `?page=${page}` : ''}`}>
        <div className="public-list">
          <h1>{TEXT[locale].all}</h1>
          {(body?.items ?? []).length === 0 && <p>{TEXT[locale].empty}</p>}
          {body?.items.map(s => <a key={s.slug} href={`/martyr/${encodeURIComponent(s.slug)}`}><span><b>{s.honorific ? `${s.honorific} ` : ''}{s.fullName}</b><small>{s.shortDescription}</small></span></a>)}
          {body && (page + 1) * body.size < body.total && <a href={`/martyr?page=${page + 1}`}>›</a>}
        </div>
      </Document>), 'public, max-age=300');
  }
  const match = /^\/martyr\/([a-z0-9]+(?:-[a-z0-9]+)*)$/.exec(path);
  if (match) {
    const slug = match[1]!;
    const requestedLocale = url.searchParams.get('lang');
    const {status, body: profile} = await bff<Profile>(`/public/profiles/${slug}${isLocale(requestedLocale) ? `?locale=${requestedLocale}` : ''}`);
    if (!profile) {
      const locale: Locale = 'fa';
      return send(res, status === 404 ? 404 : 502, 'text/html; charset=utf-8', html(
        <Document locale={locale} title={`${TEXT[locale].notFound} | ${TEXT[locale].site}`} robots="noindex"><div className="public-list"><h1>{TEXT[locale].notFound}</h1><a href="/martyr">{TEXT[locale].all}</a></div></Document>), 'no-store');
    }
    const locale: Locale = isLocale(requestedLocale) ? requestedLocale : isLocale(profile.contentLocale) ? profile.contentLocale : 'fa';
    const {body: books} = await bff<Book[]>(`/public/profiles/${slug}/books?locale=${locale}`);
    const canonical = `${ORIGIN}${profileUrl(profile.slug, locale)}`;
    const image = profile.portrait ? `${ORIGIN}/api/routes/martyrs/public/media/${profile.portrait.mediaId}` : undefined;
    const title = `${profile.seo?.title ?? profile.fullName} | ${TEXT[locale].site}`;
    const description = profile.seo?.description ?? profile.shortDescription;
    const jsonLd = {'@context': 'https://schema.org', '@type': 'Person', name: profile.fullName, ...(description ? {description} : {}), ...(image ? {image} : {}), url: canonical,
      mainEntityOfPage: {'@type': 'ProfilePage', '@id': canonical, dateModified: profile.updatedAt, datePublished: profile.firstPublishedAt}};
    return send(res, 200, 'text/html; charset=utf-8', html(
      <Document locale={locale} title={title} description={description} canonical={canonical} alternateSlug={profile.slug} image={image} jsonLd={jsonLd}><ProfilePage profile={profile} books={books ?? []} locale={locale} /></Document>), 'public, max-age=300');
  }
  return send(res, 404, 'text/plain', 'Not found', 'no-store');
}

createServer((req, res) => {
  handle(req, res).catch(error => {
    console.error(JSON.stringify({level: 'error', message: 'render failed', error: String(error)}));
    if (!res.headersSent) send(res, 502, 'text/plain', 'Temporarily unavailable', 'no-store');
  });
}).listen(PORT, () => console.log(JSON.stringify({level: 'info', message: `public-web listening on ${PORT}`, bff: BFF})));
