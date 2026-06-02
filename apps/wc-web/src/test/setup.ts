import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

// matchMedia is not implemented in jsdom. Provide a default no-match stub so
// components that read `prefers-color-scheme` / `prefers-reduced-motion` don't
// throw. Individual tests override `window.matchMedia` to assert resolution.
function installDefaultMatchMedia(): void {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

beforeEach(() => {
  localStorage.clear();
  document.documentElement.removeAttribute('data-theme');
  installDefaultMatchMedia();
});

afterEach(() => {
  cleanup();
});
