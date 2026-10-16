// Builds every artifact and container image of the local stack.
//   node tools/images.mjs            HaramYar images (backends, MFEs, shell/gateway, public-web, Keycloak)
//   node tools/images.mjs --hive     also builds hive/bff and hive/authorization from the pinned Hive checkout
// Requires the Hive Spring Boot starter in the local Maven repository (see docs/DEVELOPMENT.md).
import {execFileSync} from 'node:child_process';
import {cpSync, readFileSync, rmSync} from 'node:fs';
import {join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const windows = process.platform === 'win32';
const run = (command, args, cwd = root) => {
  console.log(`\n> ${command} ${args.join(' ')}  (${cwd})`);
  execFileSync(command, args, {cwd, stdio: 'inherit', shell: windows});
};

if (process.argv.includes('--hive')) {
  const lock = JSON.parse(readFileSync(join(root, 'hive.lock.json'), 'utf8'));
  const hive = resolve(process.env.HIVE_PLATFORM_DIR ?? join(root, '..', 'hive-platform'));
  run(windows ? join(hive, 'mvnw.cmd') : './mvnw', ['-B', '-q', '-pl', 'services/bff,services/authorization', '-am', 'install', '-DskipTests'], hive);
  run('docker', ['build', '-t', 'hive/bff:0.1.0-dev', 'services/bff'], hive);
  run('docker', ['build', '-t', 'hive/authorization:0.1.0-dev', 'services/authorization'], hive);
  console.log(`Hive images built from ${hive} (pinned commit ${lock.commit}).`);
}

run(windows ? join(root, 'mvnw.cmd') : './mvnw', ['-B', '-q', 'package', '-DskipTests']);
run('npm', ['run', 'build']);
// Stage the pinned Hive CLI (with its SDK dependency) and the built module manifests for the bootstrap image.
{
  const hive = resolve(process.env.HIVE_PLATFORM_DIR ?? join(root, '..', 'hive-platform'));
  const stage = join(root, 'integrations', '.hive-cli');
  rmSync(stage, {recursive: true, force: true});
  cpSync(join(hive, 'cli'), join(stage, 'cli'), {recursive: true, filter: p => !p.includes('node_modules')});
  cpSync(join(root, 'vendor', 'hive-sdk', 'contracts'), join(stage, 'contracts'), {recursive: true});
  const modules = join(root, 'integrations', '.modules');
  rmSync(modules, {recursive: true, force: true});
  for (const key of ['rozeh-booking', 'martyrs']) {
    cpSync(join(root, 'modules', key, 'manifests'), join(modules, key, 'manifests'), {recursive: true});
    cpSync(join(root, 'modules', key, 'frontend', 'dist', 'hive'), join(modules, key, 'frontend', 'dist', 'hive'), {recursive: true});
  }
}
run('docker', ['compose', '-f', 'deployment/docker-compose.yml', '--env-file', 'deployment/.env', '--profile', 'bootstrap', 'build']);
console.log('\nAll images built.');
