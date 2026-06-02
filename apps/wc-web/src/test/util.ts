import { readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { expect } from 'vitest';

/** Recursively list files under `dir` whose basename matches `pattern`. */
export function listFiles(dir: string, pattern: RegExp): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry === 'dist' || entry === 'coverage') {
      continue;
    }
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...listFiles(full, pattern));
    } else if (pattern.test(entry)) {
      out.push(full);
    }
  }
  return out;
}

/** Narrow an unknown to a string-keyed record, asserting it is a non-null object. */
export function asRecord(value: unknown): Record<string, unknown> {
  expect(value).toBeTypeOf('object');
  expect(value).not.toBeNull();
  return value as Record<string, unknown>;
}

/** Collect every leaf string in a (possibly nested) plain-object tree. */
export function collectLeafStrings(
  value: unknown,
  acc: string[] = [],
): string[] {
  if (typeof value === 'string') {
    acc.push(value);
  } else if (value && typeof value === 'object') {
    for (const v of Object.values(value as Record<string, unknown>)) {
      collectLeafStrings(v, acc);
    }
  }
  return acc;
}

/**
 * Strip CSS/JS block comments and line comments (the latter only when not part
 * of a `://` URL) so comment prose is never parsed as code or selectors.
 */
export function stripComments(text: string): string {
  return text
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1');
}

/**
 * Read a CSS custom-property value from the first flat rule block whose selector
 * text includes `selectorIncludes`. Assumes non-nested blocks (the token layer
 * is flat — no @media nesting). Comments are stripped first so commentary that
 * mentions a selector can't be mistaken for one. Returns the trimmed value or null.
 */
export function readVarInSelector(
  css: string,
  selectorIncludes: string,
  varName: string,
): string | null {
  const escaped = varName.replace(/-/g, '\\-');
  const decl = new RegExp(`${escaped}\\s*:\\s*([^;]+);`);
  for (const match of stripComments(css).matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    const sel = match[1];
    const body = match[2];
    if (sel === undefined || body === undefined) {
      continue;
    }
    if (sel.includes(selectorIncludes)) {
      const m = body.match(decl);
      const value = m?.[1];
      if (value !== undefined) {
        return value.trim();
      }
    }
  }
  return null;
}
