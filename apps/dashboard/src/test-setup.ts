import "@testing-library/jest-dom/vitest";
import { vi } from "vitest";
Object.defineProperty(window, "matchMedia", {
  value: vi.fn(() => ({
    matches: false,
    addListener() {},
    removeListener() {},
  })),
});
globalThis.IntersectionObserver = class {
  observe() {}
  disconnect() {}
  unobserve() {}
} as unknown as typeof IntersectionObserver;

HTMLElement.prototype.scrollIntoView = vi.fn();
window.scrollTo = vi.fn();
