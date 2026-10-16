// Links the Hive SDK packages of the pinned Hive commit (hive.lock.json) into vendor/hive-sdk as npm workspace
// packages, so HaramYar frontends depend on "@hive-platform/*" by name without copying Hive source into this repository.
// Usage: HIVE_PLATFORM_DIR=../hive-platform node tools/hive-sdk.mjs   (run `npm ci && npm run build` in Hive first)
import {cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const lock = JSON.parse(readFileSync(join(root, 'hive.lock.json'), 'utf8'));
const hive = resolve(process.env.HIVE_PLATFORM_DIR ?? join(root, '..', 'hive-platform'));
if (!existsSync(join(hive, 'packages'))) {
  console.error(`Hive checkout not found at ${hive}. Clone ${lock.repository} at ${lock.commit} and set HIVE_PLATFORM_DIR.`);
  process.exit(1);
}
try {
  const head = execFileSync('git', ['-C', hive, 'rev-parse', 'HEAD'], {encoding: 'utf8'}).trim();
  if (head !== lock.commit) console.warn(`warning: Hive checkout is at ${head}, hive.lock.json pins ${lock.commit}`);
} catch { console.warn('warning: could not determine the Hive checkout commit'); }

const target = join(root, 'vendor', 'hive-sdk');
rmSync(target, {recursive: true, force: true});
mkdirSync(target, {recursive: true});
for (const name of lock.sdkPackages) {
  const source = join(hive, 'packages', name);
  if (!existsSync(join(source, 'dist', 'index.js'))) {
    console.error(`packages/${name}/dist is missing: run "npm ci && npm run build" in ${hive}`);
    process.exit(1);
  }
  const manifest = JSON.parse(readFileSync(join(source, 'package.json'), 'utf8'));
  delete manifest.scripts;
  delete manifest.devDependencies;
  mkdirSync(join(target, name), {recursive: true});
  cpSync(join(source, 'dist'), join(target, name, 'dist'), {recursive: true});
  writeFileSync(join(target, name, 'package.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log(`linked ${manifest.name}@${manifest.version}`);
}
writeFileSync(join(target, 'HIVE_COMMIT'), lock.commit + '\n');
