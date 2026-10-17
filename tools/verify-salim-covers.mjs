// Verify curated originals and delivery assets offline. No credentials or data writes.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
const root = fileURLToPath(new URL('../', import.meta.url));
const read = path => readFileSync(resolve(root, path));
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const covers = JSON.parse(read('docs/martyrs/salim-covers.json'));
const books = JSON.parse(read('docs/martyrs/salim-editions-inventory.json')).books;
assert.equal(covers.length, books.length);
assert.equal(new Set(covers.map(c => c.bookId)).size, books.length);
assert.equal(new Set(covers.map(c => c.sourceId)).size, books.length);
for (const cover of covers) {
  const book = books.find(b => b.sourceId === cover.sourceId);
  assert.equal(cover.bookTitle, book.originalTitle);
  if (cover.matchStatus === 'missing') {
    assert.ok(cover.reason);
    assert.equal(cover.storedImage, null);
    continue;
  }
  assert.equal(cover.matchStatus, 'verified');
  assert.equal(new URL(cover.originalImageUrl).hostname, 'www.shahidsalim.com');
  const original = read(cover.originalAsset);
  assert.equal(hash(original), cover.sha256);
  assert.equal(original.subarray(1, 4).toString(), 'PNG');
  assert.equal(original.readUInt32BE(16), cover.width);
  assert.equal(original.readUInt32BE(20), cover.height);
  for (const file of [cover.web180, cover.web360]) {
    const web = read(`apps/public-web/assets/covers/web/${file}`);
    assert.equal(web.subarray(8, 12).toString(), 'WEBP');
    assert.ok(file.endsWith(`-${hash(web).slice(0, 12)}.webp`));
  }
  assert.equal(cover.storedImage, `/martyr/_assets/covers/${cover.web360}`);
}
console.log(`Verified ${covers.length} mappings: ${covers.filter(c => c.matchStatus === 'verified').length} originals and ${covers.filter(c => c.matchStatus === 'missing').length} honest placeholders.`);
