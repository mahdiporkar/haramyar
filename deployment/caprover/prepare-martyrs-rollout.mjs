#!/usr/bin/env node
// Stages only the two follow-up CapRover images. This script does not access
// credentials, write CapRover config, or contact the production environment.
import {cpSync, existsSync, mkdtempSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {join, resolve} from 'node:path';

const root = resolve(import.meta.dirname, '../..');
const outputRoot = resolve(root, '../.build-tools/caprover');
const hive = resolve(root, '../hive-platform');
const lock = JSON.parse(readFileSync(join(root, 'hive.lock.json'), 'utf8'));
const actualHiveCommit = readFileSync(join(hive, '.git/HEAD'), 'utf8').trim();
if (actualHiveCommit !== lock.commit) throw new Error('Hive checkout is not pinned to hive.lock.json');
if (!existsSync(join(root, 'modules/martyrs/frontend/dist/hive/mf-manifest.json')))
  throw new Error('Build the martyrs micro-frontend before preparing the rollout');

mkdirSync(outputRoot, {recursive: true});
const workspace = mkdtempSync(join(outputRoot, 'martyrs-rollout-'));
const copy = (from, to) => {
  mkdirSync(resolve(to, '..'), {recursive: true});
  cpSync(from, to, {recursive: true});
};
const definition = directory => writeFileSync(join(directory, 'captain-definition'),
  JSON.stringify({schemaVersion: 2, dockerfilePath: './Dockerfile'}));
const stage = (directory, basename) => {
  definition(directory);
  console.log(`Stage ready (no credentials): ${directory}`);
  console.log(`Package via: tar -cf ${join(outputRoot, basename)} -C ${directory} .`);
};

// Registration-only image: Hive manifest/route registration, no realm or staff-account mutations.
const bootstrap = join(workspace, 'bootstrap');
mkdirSync(bootstrap, {recursive: true});
copy(join(root, 'integrations/Dockerfile.registration-only'), join(bootstrap, 'Dockerfile'));
copy(join(hive, 'cli'), join(bootstrap, '.hive-cli/cli'));
copy(join(root, 'vendor/hive-sdk/contracts'), join(bootstrap, '.hive-cli/contracts'));
copy(join(root, 'integrations/hive/register.mjs'), join(bootstrap, 'hive/register.mjs'));
copy(join(root, 'integrations/hive/access.json'), join(bootstrap, 'hive/access.json'));
copy(join(root, 'integrations/hive/solution'), join(bootstrap, 'hive/solution'));
for (const name of ['rozeh-booking', 'martyrs']) {
  for (const part of ['manifests', 'frontend/dist/hive'])
    copy(join(root, 'modules', name, part), join(bootstrap, '.modules', name, part));
}
stage(bootstrap, 'martyrs-registration.tar');

// Keep the previous version URL live while the new MFE is registered and activated.
const frontend = join(workspace, 'frontend');
mkdirSync(frontend, {recursive: true});
copy(join(root, 'modules/martyrs/frontend/dist/release'), join(frontend, 'html'));
writeFileSync(join(frontend, 'nginx.conf'),
  'server { listen 8080; root /usr/share/nginx/html; location = /healthz { return 200 "ok"; } location / { try_files $uri =404; } }\n');
writeFileSync(join(frontend, 'Dockerfile'),
  'FROM nginx:1.29.1-alpine\nCOPY html/ /usr/share/nginx/html/\nCOPY nginx.conf /etc/nginx/conf.d/default.conf\nEXPOSE 8080\nHEALTHCHECK CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1\n');
stage(frontend, 'martyrs-frontend.tar');
