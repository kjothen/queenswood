import { defineConfig } from "vite";
import { svelte } from "@sveltejs/vite-plugin-svelte";

export default defineConfig({
  plugins: [svelte()],
  server: {
    proxy: {
      "/v1": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
