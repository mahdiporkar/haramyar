import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

/**
 * One self-contained ES module (React bundled in, CSS embedded as text for the shadow root), as required by Hive's
 * ES_MODULE artifact format: the runtime verifies its SRI and evaluates exactly these bytes.
 */
export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: {'process.env.NODE_ENV': JSON.stringify('production')},
  build: {
    lib: {entry: 'src/entry.tsx', formats: ['es'], fileName: () => 'entry.js'},
    outDir: 'dist/mfe',
    emptyOutDir: true,
    target: 'es2022',
    cssCodeSplit: false,
    sourcemap: false,
    rollupOptions: {output: {inlineDynamicImports: true}},
  },
});
