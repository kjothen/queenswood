import { defineConfig } from "vite";
import { svelte } from "@sveltejs/vite-plugin-svelte";

export default defineConfig({
  plugins: [svelte()],
  server: {
    // Allow Vite to serve `?raw` imports from the repo's docs/ tree
    // (one level above this brick's project root).
    fs: { allow: ["../.."] },
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
