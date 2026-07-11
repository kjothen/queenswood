import { chromium } from 'playwright';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Renders each Excalidraw scene in the parent folder to a light and a dark
// SVG (transparent background), suitable for a GitHub <picture> block.
//
//   node export-diagrams.mjs                 # every ../*.excalidraw
//   node export-diagrams.mjs "System Diagram.excalidraw"
//
// Output: ../<slug>-light.svg and ../<slug>-dark.svg

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const diagramsDir = path.resolve(__dirname, '..');

const slug = (name) =>
  name.replace(/\.excalidraw$/i, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

const inputs = (process.argv.slice(2).length
  ? process.argv.slice(2)
  : fs.readdirSync(diagramsDir).filter((f) => f.endsWith('.excalidraw'))
).map((f) => path.resolve(diagramsDir, f));

const MIME = {
  '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json',
  '.woff2': 'font/woff2', '.woff': 'font/woff', '.ttf': 'font/ttf', '.css': 'text/css',
};

const server = http.createServer((req, res) => {
  const rel = decodeURIComponent(req.url.split('?')[0]);
  const filePath = path.join(__dirname, rel === '/' ? 'export.html' : rel);
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
page.on('console', (m) => { if (m.type() === 'error') console.error('page error:', m.text()); });
await page.goto(`http://localhost:${port}/export.html`, { waitUntil: 'load' });
await page.waitForFunction(() => window.ExcalidrawLib && typeof window.doExport === 'function', { timeout: 30000 });
await page.waitForTimeout(1500); // let fonts register

for (const input of inputs) {
  const scene = JSON.parse(fs.readFileSync(input, 'utf8'));
  for (const [dark, suffix] of [[false, 'light'], [true, 'dark']]) {
    const svg = await page.evaluate(([s, d]) => window.doExport(s, d), [scene, dark]);
    const out = path.join(diagramsDir, `${slug(path.basename(input))}-${suffix}.svg`);
    fs.writeFileSync(out, svg);
    console.log(`wrote ${path.relative(diagramsDir, out)} (${svg.length} bytes)`);
  }
}

await browser.close();
server.close();
