/**
 * HaramYar public site: server-rendered, crawlable pages for published martyr profiles. Data comes only from Hive
 * PUBLIC route operations through the BFF (drafts are structurally unreachable there); no session, cookies or tokens
 * are involved. Canonical URLs: /martyr/{slug}; the interactive app lives at /martyrs/profiles/{slug}.
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
const CSS = readFileSync(join(fileURLToPath(new URL('.', import.meta.url)), 'site.css'));

type Calendar = 'SOLAR_HIJRI' | 'LUNAR_HIJRI' | 'GREGORIAN';
interface LifeEvent {date?: {year: number; month?: number; day?: number; calendar: Calendar}; place?: string}
interface Media {mediaId: string; caption?: string; credit?: string}
interface Source {id: string; kind: string; title: string; author?: string; publisher?: string; year?: number; url?: string; note?: string}
interface Profile {
  slug: string; fullName: string; honorific?: string; contentLocale: string; shortDescription?: string; biography?: string; birth?: LifeEvent; martyrdom?: LifeEvent;
  portrait?: Media; gallery: Media[]; sources: Source[]; stories: {id: string; title: string; body: string}[]; seo?: {title?: string; description?: string}; firstPublishedAt: string; updatedAt: string;
}
interface Summary {slug: string; fullName: string; honorific?: string; shortDescription?: string; portraitMediaId?: string}
interface Book {id: string; kind: 'AUTHORED' | 'ABOUT'; sourceUrl: string; selectedEdition: {title: string; summary?: string; locale: Locale};
  availableEditions: {id: string; title: string; locale: Locale}[]}
const LANGUAGE_NAMES: Record<Locale, string> = {fa: 'فارسی', ar: 'العربية', en: 'English'};

const TEXT = {
  fa: {site: 'حرم‌یار', archive: 'معرفی شهدا', born: 'تولد', martyred: 'شهادت', biography: 'زندگی‌نامه', stories: 'خاطرات و روایت‌ها', sources: 'منابع و مراجع', openApp: 'مشاهده در اپلیکیشن حرم‌یار', notFound: 'این پرونده در دسترس نیست', all: 'همه پرونده‌ها', empty: 'هنوز پرونده‌ای منتشر نشده است', note: 'محتوای حرم‌یار تنها پس از بررسی منبع و تأیید تحریریه منتشر می‌شود.', cal: {SOLAR_HIJRI: 'ه‍.ش', LUNAR_HIJRI: 'ه‍.ق', GREGORIAN: 'م'}},
  ar: {site: 'حرم‌يار', archive: 'التعريف بالشهداء', born: 'الولادة', martyred: 'الشهادة', biography: 'السيرة', stories: 'الذكريات والروايات', sources: 'المصادر والمراجع', openApp: 'العرض في تطبيق حرم‌يار', notFound: 'هذا الملف غير متاح', all: 'كل الملفات', empty: 'لم يُنشر أي ملف بعد', note: 'لا يُنشر محتوى حرم‌يار إلا بعد التحقق من المصدر واعتماد التحرير.', cal: {SOLAR_HIJRI: 'هـ.ش', LUNAR_HIJRI: 'هـ', GREGORIAN: 'م'}},
  en: {site: 'Haramyar', archive: 'Martyrs archive', born: 'Born', martyred: 'Martyred', biography: 'Biography', stories: 'Memories and stories', sources: 'Sources and references', openApp: 'Open in the Haramyar app', notFound: 'This profile is not available', all: 'All profiles', empty: 'No profile has been published yet', note: 'Haramyar publishes content only after source review and editorial approval.', cal: {SOLAR_HIJRI: 'SH', LUNAR_HIJRI: 'AH', GREGORIAN: 'CE'}},
} as const;

async function bff<T>(path: string): Promise<{status: number; body: T | null}> {
  const response = await fetch(`${BFF}/api/routes/martyrs${path}`, {headers: {Accept: 'application/json', 'X-Correlation-Id': crypto.randomUUID()}, redirect: 'manual', signal: AbortSignal.timeout(8000)});
  return {status: response.status, body: response.ok ? await response.json() as T : null};
}

const escapeXml = (value: string) => value.replace(/[<>&'"]/g, c => ({'<': '&lt;', '>': '&gt;', '&': '&amp;', "'": '&apos;', '"': '&quot;'})[c]!);
const paragraphs = (text?: string) => (text ?? '').split(/\n\s*\n/).map(p => p.trim()).filter(Boolean);
const lifeEvent = (event: LifeEvent | undefined, locale: Locale) => {
  if (!event) return null;
  const d = event.date;
  return [d ? `${[d.day, d.month, d.year].filter(Boolean).join('/')} ${TEXT[locale].cal[d.calendar]}` : null, event.place].filter(Boolean).join(' · ') || null;
};

function Document({locale, title, description, canonical, image, jsonLd, robots, children}: {locale: Locale; title: string; description?: string; canonical?: string; image?: string; jsonLd?: object; robots?: string; children: React.ReactNode}) {
  return (
    <html lang={locale} dir={directionOf(locale)}>
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <title>{title}</title>
        {description && <meta name="description" content={description} />}
        {robots && <meta name="robots" content={robots} />}
        {canonical && <link rel="canonical" href={canonical} />}
        <meta property="og:site_name" content={TEXT[locale].site} />
        <meta property="og:title" content={title} />
        {description && <meta property="og:description" content={description} />}
        {canonical && <meta property="og:url" content={canonical} />}
        <meta property="og:type" content={jsonLd ? 'profile' : 'website'} />
        <meta property="og:locale" content={locale === 'fa' ? 'fa_IR' : locale === 'ar' ? 'ar_IQ' : 'en_US'} />
        {image && <meta property="og:image" content={image} />}
        <meta name="twitter:card" content={image ? 'summary_large_image' : 'summary'} />
        <link rel="icon" type="image/png" href="/assets/brand/haramyar-app-icon-192.png" />
        <link rel="stylesheet" href="/martyr/_assets/site.css" />
        {jsonLd && <script type="application/ld+json" dangerouslySetInnerHTML={{__html: JSON.stringify(jsonLd).replace(/</g, '\\u003c')}} />}
      </head>
      <body>
        <div className="hy hy-tokens" data-theme="default" dir={directionOf(locale)} lang={locale}>
          <header className="public-header"><a href="/"><img src="/assets/brand/haramyar-header-logo.svg" alt="" /> {TEXT[locale].site}</a><a href="/martyr">{TEXT[locale].archive}</a></header>
          <main className="public-main">{children}</main>
          <footer className="public-footer">{TEXT[locale].note} · <a href="/">haramyar</a></footer>
        </div>
      </body>
    </html>
  );
}

function ProfilePage({profile, books}: {profile: Profile; books: Book[]}) {
  const locale: Locale = isLocale(profile.contentLocale) ? profile.contentLocale : 'fa';
  const text = TEXT[locale];
  const born = lifeEvent(profile.birth, locale);
  const died = lifeEvent(profile.martyrdom, locale);
  return (
    <div className="reading-page">
      <section className="profile-cover">
        <div className="large-portrait">{profile.portrait && <img src={`/api/routes/martyrs/public/media/${profile.portrait.mediaId}`} alt={profile.portrait.caption ?? profile.fullName} />}</div>
        <div>
          {profile.honorific && <small>{profile.honorific}</small>}
          <h1>{profile.fullName}</h1>
          {(born || died) && <p>{born && <>{text.born}: {born}</>}{born && died && <br />}{died && <>{text.martyred}: {died}</>}</p>}
        </div>
      </section>
      <article itemScope itemType="https://schema.org/Person">
        <meta itemProp="name" content={profile.fullName} />
        {profile.shortDescription && <p className="lead" itemProp="description">{profile.shortDescription}</p>}
        <a className="btn btn-primary open-in-app" href={`/martyrs/profiles/${encodeURIComponent(profile.slug)}`}>{text.openApp}</a>
        {profile.biography && <><h2>{text.biography}</h2>{paragraphs(profile.biography).map((p, i) => <p key={i}>{p}</p>)}</>}
        {profile.stories.length > 0 && <><h2>{text.stories}</h2>{profile.stories.map(s => <section key={s.id}><h3>{s.title}</h3>{paragraphs(s.body).map((p, i) => <p key={i}>{p}</p>)}</section>)}</>}
        {books.length > 0 && <section className="books-section">
          <h2>{locale === 'fa' ? 'کتابخانه آثار' : locale === 'ar' ? 'مكتبة المؤلفات' : 'Library of works'}</h2>
          <ul className="books-list">{books.map(book => <li key={book.id}>
            <h3 lang={book.selectedEdition.locale}>{book.selectedEdition.title}</h3>
            {book.selectedEdition.summary && <p lang={book.selectedEdition.locale}>{book.selectedEdition.summary}</p>}
            {book.availableEditions.map(e => <a key={e.id} href={`/api/routes/martyrs/public/books/${e.id}/file`} target="_blank" rel="noopener noreferrer">
              PDF · {LANGUAGE_NAMES[e.locale]}
            </a>)}
          </li>)}</ul>
        </section>}
        <h2>{text.sources}</h2>
        <div className="sources"><ul>{profile.sources.map(s => (
          <li key={s.id}><cite>{s.title}</cite>{[s.author, s.publisher, s.year].filter(Boolean).length > 0 && <> — {[s.author, s.publisher, s.year].filter(Boolean).join('، ')}</>}
            {s.url && <> · <a href={s.url} rel="nofollow noopener noreferrer">{s.url}</a></>}</li>
        ))}</ul></div>
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
    const {status, body: profile} = await bff<Profile>(`/public/profiles/${slug}`);
    if (!profile) {
      const locale: Locale = 'fa';
      return send(res, status === 404 ? 404 : 502, 'text/html; charset=utf-8', html(
        <Document locale={locale} title={`${TEXT[locale].notFound} | ${TEXT[locale].site}`} robots="noindex"><div className="public-list"><h1>{TEXT[locale].notFound}</h1><a href="/martyr">{TEXT[locale].all}</a></div></Document>), 'no-store');
    }
    const locale: Locale = isLocale(profile.contentLocale) ? profile.contentLocale : 'fa';
    const {body: books} = await bff<Book[]>(`/public/profiles/${slug}/books?locale=${locale}`);
    const canonical = `${ORIGIN}/martyr/${encodeURIComponent(profile.slug)}`;
    const image = profile.portrait ? `${ORIGIN}/api/routes/martyrs/public/media/${profile.portrait.mediaId}` : undefined;
    const title = `${profile.seo?.title ?? profile.fullName} | ${TEXT[locale].site}`;
    const description = profile.seo?.description ?? profile.shortDescription;
    const jsonLd = {'@context': 'https://schema.org', '@type': 'Person', name: profile.fullName, ...(description ? {description} : {}), ...(image ? {image} : {}), url: canonical,
      mainEntityOfPage: {'@type': 'ProfilePage', '@id': canonical, dateModified: profile.updatedAt, datePublished: profile.firstPublishedAt}};
    return send(res, 200, 'text/html; charset=utf-8', html(
      <Document locale={locale} title={title} description={description} canonical={canonical} image={image} jsonLd={jsonLd}><ProfilePage profile={profile} books={books ?? []} /></Document>), 'public, max-age=300');
  }
  return send(res, 404, 'text/plain', 'Not found', 'no-store');
}

createServer((req, res) => {
  handle(req, res).catch(error => {
    console.error(JSON.stringify({level: 'error', message: 'render failed', error: String(error)}));
    if (!res.headersSent) send(res, 502, 'text/plain', 'Temporarily unavailable', 'no-store');
  });
}).listen(PORT, () => console.log(JSON.stringify({level: 'info', message: `public-web listening on ${PORT}`, bff: BFF})));
