#!/usr/bin/env node
// Validates the built micro-frontend and resource manifests with the pinned Hive CLI (offline, SRI verified), then
// checks HaramYar's own release invariants that Hive cannot know about:
//   - frontend package version == manifestVersion, and the versioned release artifact exists and matches the SRI;
//   - every role grant in integrations/hive/access.json names a declared resource and one of its actions;
//   - no Hive platform role key is granted by HaramYar.
// Run after `npm run build`. HIVE_PLATFORM_DIR overrides the sibling Hive checkout.
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {existsSync, readFileSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const hive = resolve(process.env.HIVE_PLATFORM_DIR ?? join(root, '..', 'hive-platform'));
const modules = ['rozeh-booking', 'martyrs'];
const actionKeys = actions => new Set((actions ?? []).map(a => typeof a === 'string' ? a : a.key));
const json = file => JSON.parse(readFileSync(file, 'utf8'));
const failures = [];
const fail = message => failures.push(message);

// 1. Hive's own validator (authoritative rules mirrored from the control plane).
const cli = join(hive, 'cli', 'bin', 'hive.mjs');
if (!existsSync(cli)) {
  fail(`Hive CLI not found at ${cli} (check out hive-platform at the commit pinned in hive.lock.json)`);
} else {
  const dirs = modules.map(m => join(root, 'modules', m, 'frontend', 'dist', 'hive'));
  const missing = dirs.filter(d => !existsSync(join(d, 'mf-manifest.json')));
  if (missing.length) fail(`build the micro-frontends first: ${missing.join(', ')}`);
  else if (spawnSync(process.execPath, [cli, 'validate', ...dirs], {stdio: 'inherit'}).status !== 0) fail('Hive manifest validation failed');
}

// 2. Release invariants per module.
const declared = new Map();
for (const key of modules) {
  const dir = join(root, 'modules', key);
  const pkg = json(join(dir, 'frontend', 'package.json'));
  const source = json(join(dir, 'manifests', 'mf-manifest.json'));
  for (const r of json(join(dir, 'manifests', 'resource-manifest.json')).resources) declared.set(r.key, actionKeys(r.actions));
  if (pkg.version !== source.manifestVersion) fail(`${key}: frontend package version ${pkg.version} != manifestVersion ${source.manifestVersion}`);
  const built = join(dir, 'frontend', 'dist', 'hive', 'mf-manifest.json');
  const release = join(dir, 'frontend', 'dist', 'release', 'mfe', source.manifestVersion, 'entry.js');
  if (!existsSync(built)) continue;
  const mf = json(built);
  if (!existsSync(release)) { fail(`${key}: release artifact missing at ${release}`); continue; }
  const [algorithm, digest] = mf.artifact.integrity.split('-');
  const actual = createHash(algorithm).update(readFileSync(release)).digest('base64');
  if (actual !== digest) fail(`${key}: release artifact does not match the manifest integrity`);
}

// 3. Role grants reference declared resources and actions; Hive platform roles are never granted here.
const access = json(join(root, 'integrations', 'hive', 'access.json'));
for (const r of access.manualResources) declared.set(r.key, actionKeys(r.actions));
for (const role of access.roles) {
  if (!role.key.startsWith('haramyar-') && !role.key.startsWith('rozeh-') && !role.key.startsWith('martyrs-')) fail(`role ${role.key}: HaramYar roles must use a HaramYar prefix`);
  for (const [resource, actions] of Object.entries(role.grants)) {
    const known = declared.get(resource);
    if (!known) { fail(`role ${role.key}: unknown resource ${resource}`); continue; }
    for (const action of actions) if (!known.has(action)) fail(`role ${role.key}: ${resource} has no action '${action}'`);
  }
}

if (failures.length) {
  console.error(`\n${failures.length} problem(s):\n - ${failures.join('\n - ')}`);
  process.exit(1);
}
console.log('\nHaramYar manifests, release artifacts and role grants are consistent.');
