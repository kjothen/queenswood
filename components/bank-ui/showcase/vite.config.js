import { defineConfig } from "vite";
import { svelte } from "@sveltejs/vite-plugin-svelte";

// Minimal Vite config — the showcase is a self-contained dev surface
// that imports directly from ../src/. Run with `yarn vite` (or
// `yarn dev` if you wire a script into bank-ui's package.json).
export default defineConfig({
  root: __dirname,
  plugins: [svelte()],
  server: {
    fs: {
      // Showcase is at bank-ui/showcase/, components at bank-ui/src/.
      // Allow Vite to reach the sibling src tree.
      allow: [".."],
    },
  },
});
