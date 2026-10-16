import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, writeFile, rm, readFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {importIntoProfile, inspectFiles, parseCatalog, seedProfile} from '../../tools/martyrs-import-books.mjs';

const header = 'source_id,kind,original_arabic_title,arabic_pdf,persian_pdf,english_pdf,review_status';
const row = '5,AUTHORED,اتباع اهل البيت في العراق,arabic.pdf,,,RECEIVED_ARABIC_PDF_TITLE_UNVERIFIED';
const profileId = '00000000-0000-4000-8000-000000000011';

test('catalog distinguishes authored Arabic originals from absent ABOUT and translations', () => {
  const inventory = parseCatalog([header, row, '1,ABOUT,كتاب عن الشهيد,,,,UNVERIFIED_SOURCE_TITLE'].join('\n'));
  assert.equal(inventory.authored.length, 1);
  assert.equal(inventory.about.length, 1);
  assert.throws(() => parseCatalog([header, row.replace('arabic.pdf,,,', 'arabic.pdf,translation.pdf,,')].join('\n')), /translation/);
  assert.throws(() => parseCatalog([header, row, row].join('\n')), /Duplicate/);
  assert.throws(() => parseCatalog([header, row.replace('arabic.pdf', '../secrets.pdf')].join('\n')), /unsafe/);
});

test('PDF inspector hashes content and rejects duplicate bytes', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'salim-import-test-'));
  try {
    await writeFile(join(dir, 'arabic.pdf'), '%PDF-1.4\nsource\n');
    const works = parseCatalog([header, row].join('\n')).authored;
    const verified = await inspectFiles(works, dir);
    assert.equal(verified.length, 1);
    assert.match(verified[0].sha256, /^[a-f0-9]{64}$/);
    assert.equal(verified[0].bytes, 16);
    assert.equal(verified[0].path, join(dir, 'arabic.pdf'));
    await writeFile(join(dir, 'second.pdf'), '%PDF-1.4\nsource\n');
    await assert.rejects(
      inspectFiles([...works, {...works[0], sourceId: '6', filename: 'second.pdf'}], dir),
      /Duplicate PDF bytes/
    );
  } finally {
    await rm(dir, {recursive: true, force: true});
  }
});

test('authenticated import creates one draft Arabic edition, uploads once, never publishes', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'salim-import-api-test-'));
  const beforeFetch = globalThis.fetch;
  const beforeToken = process.env.HARAMYAR_EDITOR_TOKEN;
  const beforeCookie = process.env.HARAMYAR_EDITOR_SESSION_COOKIE;
  const calls = [];
  try {
    const path = join(dir, 'arabic.pdf');
    await writeFile(path, '%PDF-1.4\n');
    const id = '00000000-0000-4000-8000-000000000022';
    const editionId = '00000000-0000-4000-8000-000000000033';
    let book;
    process.env.HARAMYAR_EDITOR_TOKEN = 'test-token-only';
    delete process.env.HARAMYAR_EDITOR_SESSION_COOKIE;
    globalThis.fetch = async (url, options) => {
      const requestPath = new URL(url).pathname;
      calls.push({method: options.method, requestPath, options});
      assert.equal(options.redirect, 'manual');
      assert.equal(options.headers.Authorization, 'Bearer test-token-only');
      if (options.method === 'GET') return Response.json(book ? [book] : []);
      if (options.method === 'POST' && requestPath.endsWith('/books')) {
        book = {id, kind: 'AUTHORED', originalTitle: 'اتباع اهل البيت في العراق', editions: []};
        return Response.json(book, {status: 201});
      }
      if (options.method === 'PUT') {
        book.editions.push({id: editionId, locale: 'ar', status: 'DRAFT', version: 0, hasFile: false});
        return Response.json([book]);
      }
      if (options.method === 'POST' && requestPath.endsWith('/file')) {
        assert.ok(options.body instanceof FormData);
        assert.equal(options.body.get('file').size, 9);
        book.editions[0].hasFile = true;
        return new Response(null, {status: 204});
      }
      throw new Error('Unexpected API operation');
    };
    const works = [{sourceId: '5', title: 'اتباع اهل البيت في العراق', filename: 'arabic.pdf', path,
      sha256: '0'.repeat(64)}];
    await importIntoProfile(works, {profileId, origin: 'http://localhost:8080'});
    await importIntoProfile(works, {profileId, origin: 'http://localhost:8080'});
    assert.deepEqual(calls.map(c => c.method), ['GET', 'POST', 'PUT', 'POST', 'GET']);
    assert.ok(calls.every(c => !c.requestPath.includes('/publish') && !c.requestPath.includes('/submit')));
    await assert.rejects(
      importIntoProfile(works, {profileId, origin: 'https://other-site.example'}),
      /Only HTTPS haramyar.com/
    );
    await assert.rejects(
      importIntoProfile(works, {profileId, origin: 'https://haramyar.com:444'}),
      /Only HTTPS haramyar.com/
    );
  } finally {
    globalThis.fetch = beforeFetch;
    if (beforeToken === undefined) delete process.env.HARAMYAR_EDITOR_TOKEN;
    else process.env.HARAMYAR_EDITOR_TOKEN = beforeToken;
    if (beforeCookie === undefined) delete process.env.HARAMYAR_EDITOR_SESSION_COOKIE;
    else process.env.HARAMYAR_EDITOR_SESSION_COOKIE = beforeCookie;
    await rm(dir, {recursive: true, force: true});
  }
});

test('profile draft is three-language, sourced, and seeded without duplicates', async () => {
  const draft = JSON.parse(await readFile(new URL('../../docs/martyrs/salim-profile-draft.json', import.meta.url), 'utf8'));
  assert.equal(draft.content.contentLocale, 'ar');
  assert.match(draft.content.biography, /عز الدين سليم/);
  assert.match(draft.translations.fa.biography, /عزالدین سلیم/);
  assert.match(draft.translations.en.biography, /Izz al-Din Salim/);
  assert.ok(draft.sources.length >= 2);
  let profile;
  const translations = [];
  const calls = [];
  const request = async (method, path, body) => {
    calls.push({method, path, body});
    if (path.startsWith('/editorial/profiles?q=')) {
      return {items: profile ? [{id: profile.id, slug: profile.slug}] : [], total: profile ? 1 : 0};
    }
    if (method === 'POST' && path === '/editorial/profiles') {
      profile = {id: profileId, slug: body.slug, status: 'DRAFT', version: 0, sources: []};
      return profile;
    }
    if (method === 'GET' && path === `/editorial/profiles/${profileId}`) return profile;
    if (method === 'POST' && path.endsWith('/sources')) {
      assert.equal(body.expectedVersion, profile.version);
      profile.sources.push(body);
      profile.version++;
      return profile;
    }
    if (method === 'GET' && path.endsWith('/translations')) return translations;
    if (method === 'PUT' && path.includes('/translations/')) {
      const locale = path.split('/').at(-1);
      assert.ok(['fa', 'en'].includes(locale));
      assert.equal(body.expectedVersion, null);
      translations.push({locale, status: 'DRAFT'});
      return translations;
    }
    throw new Error(`Unexpected request: ${method} ${path}`);
  };
  assert.equal(await seedProfile(request, draft), profileId);
  assert.equal(profile.sources.length, 4);
  assert.equal(translations.length, 2);
  assert.equal(await seedProfile(request, draft), profileId);
  assert.equal(profile.sources.length, 4);
  assert.equal(translations.length, 2);
  assert.equal(calls.filter(c => c.method === 'POST' && c.path === '/editorial/profiles').length, 1);
  assert.ok(calls.every(c => !c.path.endsWith('/submit') && !c.path.endsWith('/publish')));
});
