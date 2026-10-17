import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync} from 'node:fs';

const rest = new URL('../../modules/martyrs/backend/src/main/java/ir/haramyar/martyrs/interfaces/rest/', import.meta.url);
const manifest = JSON.parse(readFileSync(new URL('../../integrations/hive/solution/services/martyrs.json', import.meta.url), 'utf8'));
const canonical = path => path.replace(/\{[^}]+\}/g, '{param}').replace(/\/+$/g, '');
const signature = (method, path) => `${method} ${canonical(path)}`;

test('every martyr public and editorial REST endpoint is registered in the Hive gateway', () => {
  const operations = new Set();
  for (const operation of manifest.operations) {
    const key = signature(operation.method, operation.pathPattern);
    assert.ok(!operations.has(key), `duplicate Hive route: ${key}`);
    assert.equal(operation.access, operation.pathPattern.startsWith('/public/') ? 'PUBLIC' : 'AUTHENTICATED', key);
    if (operation.access === 'AUTHENTICATED') {
      assert.equal(operation.resourceKey, 'martyrs.editorial', key);
      assert.ok(operation.action, `missing permission action on ${key}`);
    }
    operations.add(key);
  }

  for (const filename of readdirSync(rest).filter(name => name.endsWith('Controller.java'))) {
    const source = readFileSync(new URL(filename, rest), 'utf8');
    const prefix = source.match(/@RequestMapping\("([^"]+)"\)/)?.[1] ?? '';
    const mappings = source.matchAll(/@(Get|Post|Put|Delete)Mapping\((?:path\s*=\s*)?"([^"]+)"/g);
    for (const [, kind, path] of mappings) {
      const key = signature(kind.toUpperCase(), prefix + path);
      assert.ok(operations.has(key), `${filename}: missing Hive operation ${key}`);
    }
  }
});
