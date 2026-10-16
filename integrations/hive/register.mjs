// Registers HaramYar in a Hive installation through Hive's supported mechanisms only:
//   1. application + HaramYar-level manual resources (admin API),
//   2. modules, resource manifests, micro-frontend artifacts, service targets, proxy routes and operations
//      (the Hive CLI's own `hive register`),
//   3. business roles and their resource-action grants (admin API).
// Idempotent: existing objects are kept; new manifest versions are added and activated.
//
// Environment: HIVE_AUTHORIZATION_URL, HIVE_PROVISIONING_PASSWORD, HIVE_CLI (path to hive.mjs, default
// $HIVE_PLATFORM_DIR/cli/bin/hive.mjs), ROZEH_BOOKING_URL, MARTYRS_URL, SHELL_SERVICE_URL (internal base URLs).
import {execFileSync} from 'node:child_process';
import {cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..', '..');
const env = name => {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value.replace(/\/+$/, '');
};
const authorization = env('HIVE_AUTHORIZATION_URL');
const password = env('HIVE_PROVISIONING_PASSWORD');
const urls = {ROZEH_BOOKING_URL: env('ROZEH_BOOKING_URL'), MARTYRS_URL: env('MARTYRS_URL'), SHELL_SERVICE_URL: env('SHELL_SERVICE_URL')};
const cli = process.env.HIVE_CLI ?? join(process.env.HIVE_PLATFORM_DIR ?? join(root, '..', 'hive-platform'), 'cli', 'bin', 'hive.mjs');
const moduleDirs = {'rozeh-booking': join(root, 'modules/rozeh-booking'), martyrs: join(root, 'modules/martyrs')};
const moduleUrls = {'rozeh-booking': process.env.ROZEH_BOOKING_MFE_URL ?? urls.ROZEH_BOOKING_URL, martyrs: process.env.MARTYRS_MFE_URL ?? urls.MARTYRS_URL};

const auth = 'Basic ' + Buffer.from(`provisioner:${password}`).toString('base64');
async function call(method, path, body, allow = []) {
  const response = await fetch(authorization + path, {method, headers: {Authorization: auth, 'Content-Type': 'application/json'}, ...(body === undefined ? {} : {body: JSON.stringify(body)})});
  const text = await response.text();
  const value = text ? JSON.parse(text) : null;
  if (response.status >= 300 && !allow.includes(response.status)) throw new Error(`${method} ${path}: ${response.status} ${value?.code ?? ''} ${value?.message ?? ''}`.trim());
  console.log(`${response.status < 300 ? 'applied' : 'kept   '}  ${method} ${path}`);
  return value;
}

/** Copies the solution layout `hive register` expects, with deployment URLs substituted. */
function assemble() {
  const out = process.env.HARAMYAR_SOLUTION_DIR ?? join(tmpdir(), 'haramyar-hive-solution');
  rmSync(out, {recursive: true, force: true});
  mkdirSync(join(out, 'services'), {recursive: true});
  cpSync(join(here, 'solution', 'hive.solution.json'), join(out, 'hive.solution.json'));
  for (const file of readdirSync(join(here, 'solution', 'services'))) {
    const text = readFileSync(join(here, 'solution', 'services', file), 'utf8').replace(/\$\{([A-Z_]+)\}/g, (_, name) => {
      if (!urls[name]) throw new Error(`Unknown placeholder ${name} in ${file}`);
      return urls[name];
    });
    writeFileSync(join(out, 'services', file), text);
  }
  for (const [key, dir] of Object.entries(moduleDirs)) {
    const built = join(dir, 'frontend', 'dist', 'hive');
    if (!existsSync(join(built, 'mf-manifest.json'))) throw new Error(`${key}: build the micro-frontend first (npm run build -w ${dir.slice(root.length + 1)}/frontend)`);
    const target = join(out, 'dist', 'modules', key);
    mkdirSync(target, {recursive: true});
    const mf = JSON.parse(readFileSync(join(built, 'mf-manifest.json'), 'utf8'));
    // The artifact is served by the module's own service; only the origin depends on the deployment (SRI is unaffected).
    mf.artifact.url = `${moduleUrls[key]}${new URL(mf.artifact.url).pathname}`;
    writeFileSync(join(target, 'mf-manifest.json'), JSON.stringify(mf, null, 2));
    cpSync(join(built, 'resource-manifest.json'), join(target, 'resource-manifest.json'));
  }
  return out;
}

const access = JSON.parse(readFileSync(join(here, 'access.json'), 'utf8'));
const app = access.applicationKey;

// 1. Application and HaramYar-level resources (not owned by any business module).
await call('POST', '/admin/applications', {key: app, displayName: 'حرم‌یار'}, [409]);
for (const r of access.manualResources) await call('POST', `/admin/applications/${app}/resources`, r, [409]);

// 2. Modules, manifests, artifacts, targets, routes and operations through the Hive CLI.
const solution = assemble();
if (!existsSync(cli)) throw new Error(`Hive CLI not found at ${cli}; set HIVE_CLI or HIVE_PLATFORM_DIR`);
execFileSync(process.execPath, [cli, 'register', '--solution', solution, '--authorization', authorization], {stdio: 'inherit', env: {...process.env, HIVE_PROVISIONING_PASSWORD: password}});

// 3. Business roles and grants. Hive platform roles are never assigned here.
for (const role of access.roles) {
  await call('POST', '/admin/roles', {key: role.key, displayName: role.displayName, description: role.description}, [409]);
  for (const [resourceKey, actions] of Object.entries(role.grants))
    for (const action of actions) await call('POST', '/admin/grants', {subject: `role:${role.key}`, applicationKey: app, resourceKey, action}, [409]);
}
console.log('HaramYar registered in Hive.');
