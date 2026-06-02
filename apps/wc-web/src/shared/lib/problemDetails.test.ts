import { describe, it, expect } from 'vitest';
import { parseProblemDetail } from './problemDetails';

// The B.21 concrete example — lock attempt with unlinked planned commitments.
const PROBLEM = {
  type: 'https://api.wc.example.com/problems/unlinked-planned-commitment',
  title: 'Conflict',
  status: 409,
  detail:
    'Plan cannot lock: 2 planned commitments are not linked to a Supporting Outcome.',
  safeMessage:
    'Every planned commitment must link to a Supporting Outcome before you can lock this plan.',
  code: 'UNLINKED_PLANNED_COMMITMENT',
  constraint: 'planned_commitment_requires_supporting_outcome_at_lock',
  fieldErrors: [
    {
      field: 'commitments[3].supportingOutcomeId',
      code: 'REQUIRED',
      message: 'Supporting Outcome is required.',
    },
    {
      field: 'commitments[7].supportingOutcomeId',
      code: 'REQUIRED',
      message: 'Supporting Outcome is required.',
    },
  ],
  traceId: '0af7651916cd43dd8448eb211c80319c',
};

describe('parseProblemDetail (B.21)', () => {
  it('problem_json_parsed: a problem+json body parses into { safeMessage, code, constraint, fieldErrors }', () => {
    const r = parseProblemDetail(PROBLEM);
    expect(r.safeMessage).toBe(PROBLEM.safeMessage);
    expect(r.code).toBe('UNLINKED_PLANNED_COMMITMENT');
    expect(r.constraint).toBe(
      'planned_commitment_requires_supporting_outcome_at_lock',
    );
    expect(r.fieldErrors).toHaveLength(2);
    expect(r.fieldErrors[0]).toEqual({
      field: 'commitments[3].supportingOutcomeId',
      code: 'REQUIRED',
      message: 'Supporting Outcome is required.',
    });
  });

  it('malformed_error_body_degrades: non-problem/garbage bodies yield a generic safeMessage, never throw', () => {
    const bad: unknown[] = [
      '<html>500 Internal Server Error</html>',
      null,
      undefined,
      42,
      {},
      { detail: 'leaky internal detail' },
      { fieldErrors: 'not-an-array' },
    ];
    for (const body of bad) {
      expect(() => parseProblemDetail(body)).not.toThrow();
      const r = parseProblemDetail(body);
      expect(typeof r.safeMessage).toBe('string');
      expect(r.safeMessage.length).toBeGreaterThan(0);
      expect(r.code).toBeNull();
      expect(r.constraint).toBeNull();
      expect(r.fieldErrors).toEqual([]);
    }

    // A body with only `detail` must NOT surface detail (potential leak) — generic instead.
    expect(
      parseProblemDetail({ detail: 'leaky internal detail' }).safeMessage,
    ).toBe(parseProblemDetail('garbage').safeMessage);
  });
});
