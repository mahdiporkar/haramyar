import {test} from 'node:test';
test('Curated cover mappings, original hashes/dimensions and immutable delivery hashes agree', async () => {
  await import('../../tools/verify-salim-covers.mjs');
});
