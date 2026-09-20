import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    // ponytail: vitest recommends this to avoid re-creating jsdom per file.
    pool: "vmThreads",
    setupFiles: ["./src/test-setup.ts"],
    exclude: ["e2e/**", "node_modules/**", "dist/**"],
    coverage: {
      provider: "v8",
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/**/*.test.{ts,tsx}", "src/test-setup.ts", "src/main.tsx"],
      reporter: ["text", "json-summary"],
      thresholds: {
        statements: 72,
        branches: 67,
        functions: 72,
        lines: 74,
      },
    },
  },
});
