// Reproduce reviewed web assets from the preserved originals; never downloads or silently approves new artwork.
// npm install --prefix .local/cover-tools --no-save @napi-rs/canvas@1.0.11
// node tools/rebuild-salim-covers.mjs .local/cover-tools/node_modules/@napi-rs/canvas/index.js
import {readFileSync, writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';
const modulePath = process.argv[2];
if (!modulePath) throw new Error('Pass the installed @napi-rs/canvas@1.0.11 index.js path (see file header).');
const {createCanvas, loadImage} = await import(pathToFileURL(resolve(modulePath)).href);
const root = new URL('../', import.meta.url);
const manifestUrl = new URL('docs/martyrs/salim-covers.json', root);
const covers = JSON.parse(readFileSync(manifestUrl));
for (const cover of covers.filter(c => c.matchStatus === 'verified')) {
  const bytes = readFileSync(new URL(cover.originalAsset, root));
  if (createHash('sha256').update(bytes).digest('hex') !== cover.sha256) throw new Error(`Original changed: ${cover.sourceId}; visual re-review required.`);
  const im = await loadImage(bytes);
  for (const requested of [180, 360]) {
    const width = Math.min(requested, im.width), height = Math.round(im.height * width / im.width);
    const canvas = createCanvas(width, height);
    canvas.getContext('2d').drawImage(im, 0, 0, width, height);
    const web = canvas.toBuffer('image/webp', 85);
    const hash = createHash('sha256').update(web).digest('hex').slice(0, 12);
    const file = `${cover.sourceId}-${requested}-${hash}.webp`;
    writeFileSync(new URL(`apps/public-web/assets/covers/web/${file}`, root), web);
    cover[`web${requested}`] = file;
  }
  cover.storedImage = `/martyr/_assets/covers/${cover.web360}`;
}
writeFileSync(manifestUrl, JSON.stringify(covers, null, 2) + '\n');
console.log('Rebuilt verified covers. Review manifest changes and remove superseded assets explicitly.');
