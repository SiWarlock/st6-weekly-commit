import { describe, it, expect } from 'vitest';
import { handlers } from './handlers';
import { meForPersona, heatmapResponse, ALL_PLANS } from './fixtures';
import { getPlanForPersona, commandCenterPage } from './db';
import type {
  Priority,
  WorkType,
  Confidence,
  AlignmentStatus,
  PlanState,
} from '../../shared/lib/dtos';

// ST.7a — the deterministic pins for the standalone MSW mock layer: (2) the
// fixtures/selectors are contract-typed bodies (compile-time via dtos.ts + a
// light runtime shape check), and (3) the fixture set spans the full styling
// range (every PlanState + every chess-field value) so the ST.7b QA exercises
// the whole skin. The boundary invariant (#1, safety) lives in boundary.test.ts.

describe('ST.7a MSW mock layer — handlers + contract-typed fixtures', () => {
  it('handlers_return_contract_typed_shapes: handlers is a non-empty registry and the representative selectors return bodies with their DTO required keys (compile-time typed via dtos.ts)', () => {
    expect(Array.isArray(handlers)).toBe(true);
    expect(handlers.length).toBeGreaterThan(0);

    // MeDto (B.3)
    const me = meForPersona('demo-employee-ic-1');
    expect(me).toMatchObject({
      employeeId: expect.any(String),
      email: expect.any(String),
      displayName: expect.any(String),
      role: expect.any(String),
      isManager: expect.any(Boolean),
    });

    // WeeklyPlanDto (B.5) — nested commitments[] + allowedActions[]
    const plan = getPlanForPersona('demo-employee-ic-1');
    expect(plan).toMatchObject({
      id: expect.any(String),
      employeeId: expect.any(String),
      state: expect.any(String),
      commitments: expect.any(Array),
      allowedActions: expect.any(Array),
      version: expect.any(Number),
    });

    // ManagerCommandCenterRowDto page (B.11 + B.20 envelope)
    const cc = commandCenterPage();
    expect(cc).toHaveProperty('content');
    expect(cc).toHaveProperty('page');
    expect(Array.isArray(cc.content)).toBe(true);
    expect(cc.content.length).toBeGreaterThan(0);

    // HeatmapResponseDto (B.12)
    const hm = heatmapResponse();
    expect(Array.isArray(hm.cells)).toBe(true);
    expect(hm.cells.length).toBeGreaterThan(0);
  });

  it('fixtures_span_lifecycle_and_chess_values: the fixture set includes a plan in each PlanState and commitments covering every Priority/WorkType/Confidence/AlignmentStatus value, plus unlinked + carried-forward (the QA exercises the full styling range)', () => {
    // Every lifecycle state appears across the scenario plans.
    const states = new Set<PlanState>(ALL_PLANS.map((p) => p.state));
    expect(states).toEqual(
      new Set<PlanState>(['DRAFT', 'LOCKED', 'RECONCILING', 'RECONCILED']),
    );

    const commitments = ALL_PLANS.flatMap((p) => p.commitments);
    expect(commitments.length).toBeGreaterThan(0);

    expect(new Set<Priority>(commitments.map((c) => c.priority))).toEqual(
      new Set<Priority>(['P0', 'P1', 'P2']),
    );
    expect(new Set<WorkType>(commitments.map((c) => c.workType))).toEqual(
      new Set<WorkType>(['STRATEGIC', 'MAINTENANCE', 'BLOCKER', 'UNPLANNED']),
    );
    expect(new Set<Confidence>(commitments.map((c) => c.confidence))).toEqual(
      new Set<Confidence>(['HIGH', 'MEDIUM', 'LOW']),
    );
    expect(
      new Set<AlignmentStatus>(commitments.map((c) => c.alignmentStatus)),
    ).toEqual(
      new Set<AlignmentStatus>(['ALIGNED', 'NEEDS_REVIEW', 'MISALIGNED']),
    );

    // At least one unlinked (no SO) and one carried-forward commitment exist.
    expect(commitments.some((c) => c.supportingOutcomeId === undefined)).toBe(
      true,
    );
    expect(
      commitments.some((c) => c.carryForwardSourceCommitmentId !== undefined),
    ).toBe(true);
  });
});
