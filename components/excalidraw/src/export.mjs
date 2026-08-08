import { chromium } from 'playwright';
import * as esbuild from 'esbuild';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

// Renders each named Excalidraw scene to a light and a dark SVG with a
// transparent background, suitable for a GitHub <picture> block. Excalidraw's
// own exportToSvg does the drawing, in a headless browser, so fonts and
// hand-drawn strokes match the editor. The hand-drawn font (Excalifont) is
// inlined as base64 because GitHub's SVG sanitiser strips external font URLs.
//
//   node src/export.mjs path/to/scene.excalidraw [more.excalidraw ...]
//
// Each SVG is written beside its scene as <slug>-light.svg / <slug>-dark.svg.
// Paths are resolved against the working directory; the caller chooses which
// scenes to render.

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const genDir = path.resolve(__dirname, '../gen');
const bundlePath = path.join(genDir, 'excalidraw-bundle.js');

const inputs = process.argv.slice(2).map((f) => path.resolve(f));
if (inputs.length === 0) {
  console.error('usage: node src/export.mjs <scene.excalidraw> [...]');
  process.exit(2);
}

// The resolved entry point sits in the library's dist/prod, alongside the
// fonts and subset worker the page fetches under /assets.
const require = createRequire(import.meta.url);
const assetDir = path.dirname(require.resolve('@excalidraw/excalidraw'));

const slug = (name) =>
  name.replace(/\.excalidraw$/i, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

// Bundle a headless `exportToSvg` (+ its deps) into one browser file. The
// 0.18 ESM entry pulls dozens of bare imports, so a bundler is the sane
// way to load it in a plain page.
fs.mkdirSync(genDir, { recursive: true });
await esbuild.build({
  entryPoints: [path.join(__dirname, 'entry.mjs')],
  bundle: true,
  format: 'iife',
  globalName: 'ExcalidrawLib',
  outfile: bundlePath,
  loader: { '.css': 'empty', '.woff2': 'dataurl', '.woff': 'dataurl', '.ttf': 'dataurl' },
  define: { 'process.env.NODE_ENV': '"production"' },
  logLevel: 'error',
});

const MIME = {
  '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json',
  '.woff2': 'font/woff2', '.woff': 'font/woff', '.ttf': 'font/ttf', '.css': 'text/css',
};

const server = http.createServer((req, res) => {
  const rel = decodeURIComponent(req.url.split('?')[0]);
  // /assets/* serves the Excalidraw library's dist (fonts, subset worker),
  // /bundle.js the generated bundle under gen/; everything else src/.
  let filePath;
  if (rel.startsWith('/assets/')) {
    filePath = path.join(assetDir, rel.slice('/assets/'.length));
  } else if (rel === '/bundle.js') {
    filePath = bundlePath;
  } else {
    filePath = path.join(__dirname, rel === '/' ? 'export.html' : rel);
  }
  fs.readFile(filePath, (err, data) => {
    if (err) { res.writeHead(404); res.end('not found'); return; }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(filePath)] || 'application/octet-stream' });
    res.end(data);
  });
});

await new Promise((r) => server.listen(0, r));
const port = server.address().port;

const browser = await chromium.launch();
const page = await browser.newPage();
page.on('console', (m) => {
  // Excalidraw logs a benign fallback when no font-subsetting worker URL
  // is wired up; it still embeds the font on the main thread. Suppress
  // just that line so real page errors stay visible.
  if (m.type() === 'error' && !m.text().includes('falling back to the main thread')) {
    console.error('page error:', m.text());
  }
});
await page.goto(`http://localhost:${port}/export.html`, { waitUntil: 'load' });
await page.waitForFunction(() => window.ExcalidrawLib && typeof window.doExport === 'function', { timeout: 30000 });

for (const input of inputs) {
  const scene = JSON.parse(fs.readFileSync(input, 'utf8'));
  for (const [dark, suffix] of [[false, 'light'], [true, 'dark']]) {
    const svg = await page.evaluate(([s, d]) => window.doExport(s, d), [scene, dark]);
    const out = path.join(path.dirname(input), `${slug(path.basename(input))}-${suffix}.svg`);
    fs.writeFileSync(out, svg);
    console.log(`wrote ${path.relative(process.cwd(), out)} (${svg.length} bytes)`);
  }
}

await browser.close();
server.close();
