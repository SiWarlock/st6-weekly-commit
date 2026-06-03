/**
 * RFC-7807 problem-details parser (Appendix B.21). Pure + total: turns an error
 * body into a Cypress-assertable, user-safe shape. Never throws on a malformed
 * body, and never surfaces internal fields (`detail`, `type`, `traceId`) — only
 * the server-designated `safeMessage` (safety rule #7). Per-endpoint
 * `transformErrorResponse` wiring lands with the domain slices (9.5+).
 */
export interface ProblemFieldError {
  field: string;
  code: string;
  message: string;
}

export interface ParsedProblem {
  safeMessage: string;
  code: string | null;
  constraint: string | null;
  fieldErrors: ProblemFieldError[];
}

const GENERIC_MESSAGE = 'Something went wrong. Please try again.';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function parseFieldErrors(value: unknown): ProblemFieldError[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const out: ProblemFieldError[] = [];
  for (const item of value) {
    if (
      isRecord(item) &&
      typeof item.field === 'string' &&
      typeof item.code === 'string' &&
      typeof item.message === 'string'
    ) {
      out.push({ field: item.field, code: item.code, message: item.message });
    }
  }
  return out;
}

export function parseProblemDetail(body: unknown): ParsedProblem {
  if (!isRecord(body)) {
    return {
      safeMessage: GENERIC_MESSAGE,
      code: null,
      constraint: null,
      fieldErrors: [],
    };
  }

  const safeMessage =
    typeof body.safeMessage === 'string' && body.safeMessage.trim().length > 0
      ? body.safeMessage.trim()
      : GENERIC_MESSAGE;
  const code = typeof body.code === 'string' ? body.code : null;
  const constraint =
    typeof body.constraint === 'string' ? body.constraint : null;
  const fieldErrors = parseFieldErrors(body.fieldErrors);

  return { safeMessage, code, constraint, fieldErrors };
}
