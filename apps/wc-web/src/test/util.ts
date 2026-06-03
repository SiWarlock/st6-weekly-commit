import { readdirSync, statSync, readFileSync, existsSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
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

/** Extract relative module specifiers (static `from`, side-effect, dynamic). */
function relativeSpecifiers(src: string): string[] {
  const specs: string[] = [];
  const patterns = [
    /(?:import|export)[^'"]*?from\s*['"]([^'"]+)['"]/g,
    /import\s*\(\s*['"]([^'"]+)['"]\s*\)/g,
    /import\s+['"]([^'"]+)['"]/g,
  ];
  for (const re of patterns) {
    let m: RegExpExecArray | null;
    while ((m = re.exec(src)) !== null) {
      const spec = m[1];
      if (spec !== undefined && spec.startsWith('.')) {
        specs.push(spec);
      }
    }
  }
  return specs;
}

/** Resolve a relative specifier to an on-disk source file, or null. */
function resolveModule(spec: string, baseDir: string): string | null {
  const base = resolve(baseDir, spec);
  const candidates = [
    base,
    `${base}.ts`,
    `${base}.tsx`,
    `${base}.d.ts`,
    join(base, 'index.ts'),
    join(base, 'index.tsx'),
  ];
  for (const candidate of candidates) {
    try {
      if (existsSync(candidate) && statSync(candidate).isFile()) {
        return candidate;
      }
    } catch {
      /* unreadable — skip */
    }
  }
  return null;
}

/**
 * Transitive closure of relative source imports reachable from `entryFile`
 * (comments stripped first). External/bare specifiers and unresolved (.css)
 * imports are excluded. Used to prove module-graph boundaries (REQ-I-008).
 */
export function importGraph(entryFile: string): Set<string> {
  const visited = new Set<string>();
  const stack: string[] = [entryFile];
  while (stack.length > 0) {
    const file = stack.pop();
    if (file === undefined || visited.has(file)) {
      continue;
    }
    visited.add(file);
    let src: string;
    try {
      src = stripComments(readFileSync(file, 'utf8'));
    } catch {
      continue;
    }
    for (const spec of relativeSpecifiers(src)) {
      const resolved = resolveModule(spec, dirname(file));
      if (resolved !== null) {
        stack.push(resolved);
      }
    }
  }
  return visited;
}
