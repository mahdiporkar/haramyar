// Generates deployment/.env with strong random local secrets (never committed; printed values are never shown).
// Usage: node deployment/generate-env.mjs [--dev-fixtures] [--force]
import {randomBytes} from 'node:crypto';
import {existsSync, writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

const target = fileURLToPath(new URL('.env', import.meta.url));
const args = new Set(process.argv.slice(2));
if (existsSync(target) && !args.has('--force')) {
  console.log(`${target} exists; keeping it (use --force to regenerate).`);
  process.exit(0);
}
const secret = (bytes = 32) => randomBytes(bytes).toString('base64url');
const values = {
  HARAMYAR_PUBLIC_ORIGIN: 'http://localhost:8080',
  POSTGRES_PASSWORD: secret(), HIVE_DB_PASSWORD: secret(), OPENFGA_DB_PASSWORD: secret(), KEYCLOAK_DB_PASSWORD: secret(),
  ROZEH_DB_PASSWORD: secret(), MARTYRS_DB_PASSWORD: secret(), SHELL_DB_PASSWORD: secret(),
  REDIS_PASSWORD: secret(), RABBITMQ_PASSWORD: secret(), S3_ACCESS_KEY: secret(12), S3_SECRET_KEY: secret(),
  HIVE_INTERNAL_PASSWORD: secret(), HIVE_PROVISIONING_PASSWORD: secret(), HIVE_VAULT_KEY: randomBytes(32).toString('base64'),
  HARAMYAR_OIDC_CLIENT_SECRET: secret(), KEYCLOAK_ADMIN_USER: 'haramyar-bootstrap', KEYCLOAK_ADMIN_PASSWORD: secret(),
  HARAMYAR_DEV_FIXTURES: args.has('--dev-fixtures') ? 'true' : 'false',
  HARAMYAR_FIXTURE_STAFF_PASSWORD: args.has('--dev-fixtures') ? `Fx-${secret(12)}9` : '',
};
writeFileSync(target, Object.entries(values).map(([k, v]) => `${k}=${v}`).join('\n') + '\n', {mode: 0o600});
console.log(`Wrote ${target} with fresh local secrets${args.has('--dev-fixtures') ? ' and development staff fixtures enabled' : ''}.`);
