#!/usr/bin/env node
/**
 * Audited, repeatable import of the rights-cleared Arabic Salim PDFs through
 * the staff-only HaramYar API. The API stores PDFs in its private MinIO bucket.
 *
 * Default mode is offline validation. --apply requires a real staff session
 * supplied in environment variables and NEVER publishes an edition.
 *
 * Usage:
 *   node tools/martyrs-import-books.mjs --source-dir ../upload
 *   HARAMYAR_EDITOR_TOKEN=... node tools/martyrs-import-books.mjs \
 *     --source-dir ../upload --profile-id <UUID> --apply
 */
import {createHash} from 'node:crypto';
import {createReadStream} from 'node:fs';
import {open, readFile, stat} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {dirname, isAbsolute, join, resolve} from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const defaultManifest = join(root, 'docs/martyrs/salim-books.csv');
const defaultProfileDraft = join(root, 'docs/martyrs/salim-profile-draft.json');
export const MAX_PDF_BYTES = 30 * 1024 * 1024;
const SOURCE = 'https://www.shahidsalim.com/books.php';

export function parseCatalog(csv) {
  const lines = csv.trim().split(/\r?\n/);
  const header = 'source_id,kind,original_arabic_title,arabic_pdf,persian_pdf,english_pdf,review_status';
  if (lines.shift() !== header) throw new Error('Unexpected catalog schema');
  const works = lines.map((line, i) => {
    // This archival inventory has no quoted or comma-containing values.
    const fields = line.split(',');
    if (fields.length !== 7) throw new Error(`Invalid CSV row ${i + 2}`);
    const [sourceId, kind, title, filename, persian, english, reviewStatus] = fields;
    if (!/^\d+$/.test(sourceId) || !['AUTHORED', 'ABOUT'].includes(kind) || !title.trim())
      throw new Error(`Invalid catalog identity on row ${i + 2}`);
    if (persian || english) throw new Error(`Row ${i + 2} claims a translation that has not been verified`);
    return {sourceId, kind, title, filename, reviewStatus};
  });
  const authored = works.filter(w => w.kind === 'AUTHORED');
  const unique = new Set(authored.map(w => w.title.normalize('NFC').trim()));
  if (unique.size !== authored.length) throw new Error('Duplicate Arabic work titles in manifest');
  if (authored.some(w => !w.filename || w.filename.includes('/') || w.filename.includes('\\') ||
      w.filename === '.' || w.filename === '..' || w.filename.includes('\0') || isAbsolute(w.filename)))
    throw new Error('Missing, absolute or unsafe PDF filename');
  if (authored.some(w => !w.reviewStatus.startsWith('RECEIVED_ARABIC_PDF')))
    throw new Error('Authored records must be marked as received, not published');
  return {authored, about: works.filter(w => w.kind === 'ABOUT')};
}

export async function inspectFiles(works, sourceDirectory) {
  const result = [];
  const seen = new Map();
  for (const work of works) {
    const path = join(sourceDirectory, work.filename);
    const metadata = await stat(path);
    if (!metadata.isFile() || metadata.size < 5 || metadata.size > MAX_PDF_BYTES)
      throw new Error(`Invalid PDF size for source #${work.sourceId}`);
    const reader = await open(path, 'r');
    try {
      const header = Buffer.alloc(5);
      if ((await reader.read(header, 0, 5, 0)).bytesRead !== 5 || header.toString() !== '%PDF-')
        throw new Error(`Invalid PDF signature for source #${work.sourceId}`);
    } finally {
      await reader.close();
    }
    const digest = createHash('sha256');
    for await (const chunk of createReadStream(path)) digest.update(chunk);
    const sha256 = digest.digest('hex');
    if (seen.has(sha256)) throw new Error(`Duplicate PDF bytes: #${seen.get(sha256)} and #${work.sourceId}`);
    seen.set(sha256, work.sourceId);
    result.push({...work, path, sha256, bytes: metadata.size});
  }
  return result;
}

function argsFrom(argv) {
  const options = {sourceDir: resolve(root, '../upload'), manifest: defaultManifest,
    profileDraft: defaultProfileDraft, origin: 'https://haramyar.com',
    profileId: undefined, ensureProfile: false, apply: false};
  for (let i = 0; i < argv.length; i++) {
    const argument = argv[i];
    if (argument === '--apply') { options.apply = true; continue; }
    if (argument === '--ensure-profile') { options.ensureProfile = true; continue; }
    if (['--source-dir', '--manifest', '--profile-draft', '--origin', '--profile-id'].includes(argument)) {
      if (!argv[i + 1]) throw new Error(`Missing value for ${argument}`);
      const value = argv[++i];
      if (argument === '--source-dir') options.sourceDir = resolve(value);
      if (argument === '--manifest') options.manifest = resolve(value);
      if (argument === '--profile-draft') options.profileDraft = resolve(value);
      if (argument === '--origin') options.origin = value;
      if (argument === '--profile-id') options.profileId = value;
      continue;
    }
    throw new Error(`Unknown option: ${argument}`);
  }
  return options;
}

export function normalizeTitle(title) { return title.normalize('NFC').trim().replace(/\s+/g, ' '); }

export async function seedProfile(request, draft) {
  if (draft.status !== 'DRAFT_FOR_EDITORIAL_REVIEW' || draft.sourceLanguage !== 'ar' ||
      draft.content?.contentLocale !== 'ar' || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(draft.slug) ||
      !draft.translations?.fa || !draft.translations?.en || !Array.isArray(draft.sources) || !draft.sources.length)
    throw new Error('Invalid Arabic source profile draft; translations require editorial review');
  let found;
  const query = encodeURIComponent(draft.content.fullName.slice(0, 12));
  let page = 0;
  while (true) {
    const results = await request('GET', `/editorial/profiles?q=${query}&size=50&page=${page++}`);
    if (!Array.isArray(results.items) || typeof results.total !== 'number')
      throw new Error('Unexpected editorial profile search response');
    found = results.items.find(profile => profile.slug === draft.slug);
    if (found || page * 50 >= results.total) break;
  }
  let profile;
  if (found) {
    profile = await request('GET', `/editorial/profiles/${found.id}`);
    console.log(`Reusing existing ${draft.slug} profile (${profile.status})`);
  } else {
    profile = await request('POST', '/editorial/profiles', {slug: draft.slug, content: draft.content});
    console.log(`Created profile ${draft.slug} as DRAFT`);
  }
  if (profile.status === 'DRAFT') {
    const sources = new Set(profile.sources.map(source => source.url));
    for (const source of draft.sources) {
      if (sources.has(source.url)) continue;
      profile = await request('POST', `/editorial/profiles/${profile.id}/sources`,
        {...source, expectedVersion: profile.version});
      sources.add(source.url);
    }
  }
  const existing = await request('GET', `/editorial/profiles/${profile.id}/translations`);
  for (const locale of ['fa', 'en']) {
    if (existing.some(t => t.locale === locale)) continue; // Never overwrite a reviewer or another editor.
    await request('PUT', `/editorial/profiles/${profile.id}/translations/${locale}`,
      {...draft.translations[locale], expectedVersion: null});
    console.log(`Staged ${locale} biography as DRAFT; reviewer approval still required`);
  }
  return profile.id;
}

export async function importIntoProfile(works, options) {
  if (options.ensureProfile && options.profileId)
    throw new Error('Choose either --profile-id or --ensure-profile, not both');
  if (!options.ensureProfile && !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(options.profileId ?? ''))
    throw new Error('--apply requires --profile-id or --ensure-profile');
  const origin = new URL(options.origin);
  if (origin.username || origin.password || origin.pathname !== '/' || origin.search || origin.hash ||
      !((origin.origin === 'https://haramyar.com') ||
        (origin.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(origin.hostname))))
    throw new Error('Only HTTPS haramyar.com or a local development origin is allowed for authenticated requests');
  const accessToken = process.env.HARAMYAR_EDITOR_TOKEN;
  const sessionCookie = process.env.HARAMYAR_EDITOR_SESSION_COOKIE;
  if (!accessToken && !sessionCookie) throw new Error('Provide HARAMYAR_EDITOR_TOKEN or HARAMYAR_EDITOR_SESSION_COOKIE via environment');
  const base = new URL('/api/routes/martyrs', origin);
  const headers = {};
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (sessionCookie) headers.Cookie = sessionCookie;
  if (process.env.HARAMYAR_EDITOR_CSRF) headers['X-CSRF-Token'] = process.env.HARAMYAR_EDITOR_CSRF;
  async function request(method, path, data) {
    const h = {...headers, Accept: 'application/json'};
    let body;
    if (data instanceof FormData) body = data;
    else if (data !== undefined) { body = JSON.stringify(data); h['Content-Type'] = 'application/json'; }
    const response = await fetch(base.origin + base.pathname + path, {method, headers: h, body,
      redirect: 'manual', signal: AbortSignal.timeout(180000)});
    if (!response.ok) {
      // Do not echo response bodies, which may expose private deployment details.
      throw new Error(`HTTP ${response.status} during ${method} ${path} (check staff permissions and BFF session)`);
    }
    return response.status === 204 ? null : response.json();
  }
  const profileId = options.ensureProfile ?
    await seedProfile(request, JSON.parse(await readFile(options.profileDraft || defaultProfileDraft, 'utf8'))) :
    options.profileId;
  const profile = encodeURIComponent(profileId);
  const path = `/editorial/profiles/${profile}/books`;
  let existing = await request('GET', path);
  if (!Array.isArray(existing)) throw new Error('Unexpected staff book API response');
  let added = 0, uploaded = 0, alreadyPresent = 0;
  for (const work of works) {
    const matches = existing.filter(b => b.kind === 'AUTHORED' &&
      normalizeTitle(b.originalTitle) === normalizeTitle(work.title));
    if (matches.length > 1) throw new Error(`Duplicate books already exist: #${work.sourceId}; fix manually`);
    let book = matches[0];
    if (!book) {
      book = await request('POST', path, {kind: 'AUTHORED', originalTitle: work.title, sourceUrl: SOURCE});
      existing.push(book);
      added++;
    }
    let edition = book.editions.find(e => e.locale === 'ar');
    if (!edition) {
      existing = await request('PUT', `/editorial/books/${book.id}/editions/ar`,
        {title: work.title, summary: '', expectedVersion: null});
      book = existing.find(b => b.id === book.id);
      edition = book?.editions.find(e => e.locale === 'ar');
    }
    if (!edition) throw new Error(`Cannot find Arabic edition for #${work.sourceId}`);
    if (edition.hasFile) {
      console.log(`SKIP #${work.sourceId}: Arabic PDF already attached (server checksum not exposed)`);
      alreadyPresent++;
      continue;
    }
    if (edition.status !== 'DRAFT') throw new Error(`Cannot upload into non-draft edition #${work.sourceId}`);
    const form = new FormData();
    form.append('file', new Blob([await readFile(work.path)], {type: 'application/pdf'}), work.filename);
    const uploadPath = `/editorial/books/editions/${edition.id}/file?expectedVersion=${edition.version}`;
    await request('POST', uploadPath, form);
    uploaded++;
    console.log(`STAGED #${work.sourceId}: ${work.sha256.slice(0, 12)}... in private MinIO (DRAFT)`);
  }
  console.log(`Finished: ${added} books created, ${uploaded} PDFs uploaded, ${alreadyPresent} previously attached. No editions published.`);
}

async function main() {
  const options = argsFrom(process.argv.slice(2));
  const inventory = parseCatalog(await readFile(options.manifest, 'utf8'));
  const works = await inspectFiles(inventory.authored, options.sourceDir);
  const bytes = works.reduce((total, w) => total + w.bytes, 0);
  console.log(`Verified ${works.length} unique Arabic authored PDFs (${bytes} bytes); ${inventory.about.length} ABOUT titles have no PDFs.`);
  if (!options.apply) {
    for (const w of works) console.log(`#${w.sourceId}\t${w.sha256}\t${w.bytes}\t${w.filename}`);
    console.log('Offline validation only: no network traffic, DB edits, uploads or publication. Use --apply --ensure-profile with staff authorization.');
    return;
  }
  await importIntoProfile(works, options);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => { console.error(`Import stopped safely: ${error.message}`); process.exitCode = 1; });
}
