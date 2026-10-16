import {defineConfig, type Plugin} from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import {createReadStream, existsSync, readdirSync, readFileSync, statSync} from 'node:fs';
import {extname, join, relative, resolve} from 'node:path';

const designAssets = resolve(__dirname, '../../shared/design-system/assets');
const TYPES: Record<string, string> = {'.svg': 'image/svg+xml', '.png': 'image/png', '.webp': 'image/webp', '.json': 'application/json', '.woff2': 'font/woff2', '.txt': 'text/plain'};

/** Serves the shared brand assets at /assets/** in development and emits them into the build. */
function designSystemAssets(): Plugin {
  const walk = (dir: string): string[] => readdirSync(dir).flatMap(name => statSync(join(dir, name)).isDirectory() ? walk(join(dir, name)) : [join(dir, name)]);
  return {
    name: 'haramyar-design-assets',
    configureServer(server) {
      server.middlewares.use('/assets', (req, res, next) => {
        const file = join(designAssets, decodeURIComponent((req.url ?? '').split('?')[0]!));
        if (!file.startsWith(designAssets) || !existsSync(file) || statSync(file).isDirectory()) return next();
        res.setHeader('Content-Type', TYPES[extname(file)] ?? 'application/octet-stream');
        createReadStream(file).pipe(res);
      });
    },
    generateBundle() {
      for (const file of walk(designAssets)) this.emitFile({type: 'asset', fileName: `assets/${relative(designAssets, file).replace(/\\/g, '/')}`, source: readFileSync(file)});
    },
  };
}

export default defineConfig({
  plugins: [react(), tailwindcss(), designSystemAssets()],
  build: {outDir: 'dist', assetsDir: 'static', sourcemap: false, target: 'es2022'},
  server: {
    port: 5173,
    // Development: the gateway (deployment/docker-compose) is the single origin; proxy Hive paths to it.
    proxy: Object.fromEntries(['/api', '/auth', '/oauth2', '/login/oauth2', '/realms', '/resources'].map(p => [p, {target: process.env.HARAMYAR_GATEWAY ?? 'http://localhost:8080', changeOrigin: false}])),
  },
});
