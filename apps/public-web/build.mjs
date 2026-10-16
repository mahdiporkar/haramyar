// Bundles the SSR server (React included) and assembles the public site stylesheet from the design system.
import {build} from 'esbuild';
import {mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';

const root = fileURLToPath(new URL('.', import.meta.url));
const styles = join(root, '../../shared/design-system/src/styles');
mkdirSync(join(root, 'dist'), {recursive: true});
await build({
  entryPoints: [join(root, 'src/server.tsx')], outfile: join(root, 'dist/server.mjs'), bundle: true, platform: 'node', format: 'esm', target: 'node22',
  jsx: 'automatic', minify: true, legalComments: 'none', logLevel: 'warning', define: {'process.env.NODE_ENV': '"production"'},
  banner: {js: "import {createRequire as __cr} from 'node:module'; const require = __cr(import.meta.url);"},
});
const css = ['base.css', 'system.css', 'extensions.css'].map(f => readFileSync(join(styles, f), 'utf8')).join('\n') + readFileSync(join(root, 'src/site.css'), 'utf8');
writeFileSync(join(root, 'dist/site.css'), css);
console.log('public-web built');
