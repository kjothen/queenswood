import { defineConfig } from "vite";
import { svelte } from "@sveltejs/vite-plugin-svelte";

export default defineConfig({
  plugins: [svelte()],
  server: {
    // 5174 keeps bank-app out of the way of bank-console (5173) so
    // both SPAs can run side-by-side in dev. strictPort refuses to
    // auto-shift onto an unwhitelisted port (5175 etc) when 5174 is
    // held by another Vite — auth fails opaquely otherwise because
    // the queenswood-ops realm only allows 5174 as a redirect URI.
    port: 5174,
    strictPort: true,
    proxy: {
      "/v1": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/oauth": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/.well-known": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
