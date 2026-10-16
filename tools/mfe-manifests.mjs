// Shared post-build step for HaramYar micro-frontends: computes the SRI of the built ES module and writes the Hive
// micro-frontend manifest (artifact URL resolved from MFE_BASE_URL) and the resource manifest into dist/hive.
// The artifact is versioned by path (/mfe/<manifestVersion>/entry.js) and served by the module's own backend.
import {createHash} from 'node:crypto';
import {transformSync} from 'esbuild';
import {copyFileSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {join} from 'node:path';

export function writeManifests({moduleDir, defaultBaseUrl}) {
  const frontend = join(moduleDir, 'frontend');
  const entry = join(frontend, 'dist', 'mfe', 'entry.js');
  // Vite keeps whitespace in ES-format library builds; minify the final artifact before its SRI is computed.
  writeFileSync(entry, transformSync(readFileSync(entry, 'utf8'), {minify: true, format: 'esm', target: 'es2022', legalComments: 'none'}).code);
  const bytes = readFileSync(entry);
  const integrity = 'sha384-' + createHash('sha384').update(bytes).digest('base64');
  const baseUrl = (process.env.MFE_BASE_URL ?? defaultBaseUrl).replace(/\/+$/, '');
  const mf = JSON.parse(readFileSync(join(moduleDir, 'manifests', 'mf-manifest.json'), 'utf8'));
  mf.artifact.url = mf.artifact.url.replace('${MFE_BASE_URL}', baseUrl);
  mf.artifact.integrity = integrity;
  const versionInPath = `/mfe/${mf.manifestVersion}/entry.js`;
  if (!mf.artifact.url.endsWith(versionInPath)) throw new Error(`artifact.url must end with ${versionInPath} (manifestVersion and path must match)`);
  const out = join(frontend, 'dist', 'hive');
  mkdirSync(out, {recursive: true});
  writeFileSync(join(out, 'mf-manifest.json'), JSON.stringify(mf, null, 2) + '\n');
  copyFileSync(join(moduleDir, 'manifests', 'resource-manifest.json'), join(out, 'resource-manifest.json'));
  // Hive's offline validator verifies a sibling entry.js against the manifest's SRI.
  copyFileSync(entry, join(out, 'entry.js'));
  console.log(`${mf.moduleKey}@${mf.manifestVersion} ${integrity} (${bytes.length} bytes) -> ${mf.artifact.url}`);
  return mf;
}
