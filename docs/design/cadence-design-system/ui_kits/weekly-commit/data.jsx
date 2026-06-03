/* =========================================================================
   ST6 Weekly Commit — seed data (mirrors UI_UX_SPEC §6/§7 fixtures)
   Exposed on window for the Babel-scoped component files.
   ========================================================================= */

/* ---- RCDO hierarchy: 1 Rally Cry / 3 Defining Objectives / 9 Supporting Outcomes ---- */
const RALLY_CRY = "Become the system of record every execution-driven team trusts by FY26.";

const RCDO_TREE = [
  {
    id: "DO-1", title: "Win customer adoption & expansion",
    outcomes: [
      { id: "SO-1.1", title: "Lift activated-team weekly-active rate to 70%" },
      { id: "SO-1.2", title: "Cut new-workspace time-to-first-locked-plan under 10 minutes" },
      { id: "SO-1.3", title: "Reach 120% net revenue retention on strategic accounts" },
    ],
  },
  {
    id: "DO-2", title: "Operational excellence in delivery",
    outcomes: [
      { id: "SO-2.1", title: "Hit 95% on-time delivery across squads" },
      { id: "SO-2.2", title: "Median support first-response under 2 business hours" },
      { id: "SO-2.3", title: "Hold weekly-commit reconciliation rate above 90%" },
    ],
  },
  {
    id: "DO-3", title: "Platform reliability & trust",
    outcomes: [
      { id: "SO-3.1", title: "Achieve 99.95% platform uptime" },
      { id: "SO-3.2", title: "Hold p95 API latency under 200ms" },
      { id: "SO-3.3", title: "Close the quarter with zero Sev-1 security incidents" },
    ],
  },
];

// flat lookup: SO id -> { rc, do, so } breadcrumb
const SO_INDEX = (() => {
  const m = {};
  RCDO_TREE.forEach((d) =>
    d.outcomes.forEach((o) => { m[o.id] = { doId: d.id, doTitle: d.title, soId: o.id, soTitle: o.title }; })
  );
  return m;
})();
function breadcrumb(soId) {
  const e = SO_INDEX[soId];
  if (!e) return null;
  return { doTitle: e.doTitle, soId: e.soId, soTitle: e.soTitle };
}

/* ---- Personas / seed cast ---- */
const WEEK_LABEL = "Week of Jun 1–7, 2026";
const NEXT_WEEK = "Jun 8–14";

// avatar tint by tone key
const PEOPLE = {
  dana:  { id: "dana",  name: "Dana Okafor",   initials: "DO", role: "Manager · also IC", tone: "brand",   isManager: true },
  priya: { id: "priya", name: "Priya Raman",   initials: "PR", role: "IC", tone: "info" },
  marco: { id: "marco", name: "Marco Bellini", initials: "MB", role: "IC", tone: "warning" },
  aisha: { id: "aisha", name: "Aisha Khan",    initials: "AK", role: "IC", tone: "failure" },
  tomas: { id: "tomas", name: "Tomas Novak",   initials: "TN", role: "IC", tone: "success" },
  grace: { id: "grace", name: "Grace Liu",     initials: "GL", role: "IC", tone: "accent" },
  sam:   { id: "sam",   name: "Sam Carter",    initials: "SC", role: "IC", tone: "neutral" },
};
const REPORT_ORDER = ["priya", "marco", "aisha", "tomas", "grace", "sam"];

/* ---- IC plans keyed by person. Each plan: state, counts, commitments[] ----
   commitment: { id, title, desc, priority, workType, confidence, alignment,
                 kind:'PLANNED'|'UNPLANNED', soId|null, outcome|null, outcomeNote,
                 carriedTo, carriedFrom, dispute? }                                   */
const PLANS = {
  // R6 Sam — DRAFT, one unlinked commitment (the lock-blocker)
  sam: {
    owner: "sam", state: "DRAFT", planned: 2, unplanned: 0,
    managerReview: null, locked: null,
    commitments: [
      { id: "c-sam-1", title: "Ship workspace onboarding telemetry events",
        desc: "Instrument the first-locked-plan funnel and wire the activation dashboards.",
        priority: "P1", workType: "STRATEGIC", confidence: "HIGH", alignment: "ALIGNED",
        kind: "PLANNED", soId: "SO-1.2" },
      { id: "c-sam-2", title: "Pair with support on the Q2 escalation backlog",
        desc: "Shadow two escalations and document the recurring triage gaps.",
        priority: "P2", workType: "MAINTENANCE", confidence: "MEDIUM", alignment: "NEEDS_REVIEW",
        kind: "PLANNED", soId: null },
    ],
  },

  // R5 Grace — RECONCILING + carry-forward chain + blocked + unplanned
  grace: {
    owner: "grace", state: "RECONCILING", planned: 3, unplanned: 1,
    managerReview: { status: "REVIEWED", reviewedAt: "Jun 1", reviewer: "Dana Okafor", overdue: false },
    locked: "Mon Jun 1, 8:51 AM CT",
    commitments: [
      { id: "c-grace-1", title: "Draft the activation-onboarding runbook",
        desc: "Write the step-by-step runbook ops uses to onboard a new activated team.",
        priority: "P1", workType: "STRATEGIC", confidence: "HIGH", alignment: "ALIGNED",
        kind: "PLANNED", soId: "SO-1.2", outcome: "COMPLETED", outcomeNote: "Shipped v1; ops reviewed." },
      { id: "c-grace-2", title: "Spec the multi-region failover runbook",
        desc: "Define the failover sequence and owner matrix for a region outage.",
        priority: "P1", workType: "STRATEGIC", confidence: "MEDIUM", alignment: "NEEDS_REVIEW",
        kind: "PLANNED", soId: "SO-3.1", outcome: "CARRIED_FORWARD", outcomeNote: "", carriedTo: NEXT_WEEK },
      { id: "c-grace-3", title: "Cut triage handoff latency",
        desc: "Reduce the support→engineering handoff to under one business hour.",
        priority: "P0", workType: "STRATEGIC", confidence: "HIGH", alignment: "ALIGNED",
        kind: "PLANNED", soId: "SO-2.2", outcome: "BLOCKED", outcomeNote: "Blocked on the Graph permissions review." },
      { id: "c-grace-4", title: "Hotfix support-portal auth regression",
        desc: "Patch the SSO redirect loop reported after the Tuesday deploy.",
        priority: "P0", workType: "UNPLANNED", confidence: "HIGH", alignment: "ALIGNED",
        kind: "UNPLANNED", soId: "SO-2.2", outcome: "COMPLETED", outcomeNote: "Patched and verified in prod." },
    ],
  },

  // R3 Aisha — LOCKED, open MISALIGNED dispute (drives IC respond + manager resolve)
  aisha: {
    owner: "aisha", state: "LOCKED", planned: 4, unplanned: 0,
    managerReview: { status: "REVIEWED_WITH_DISPUTES", reviewedAt: "Jun 1, 3:12 PM", reviewer: "Dana Okafor", overdue: false },
    locked: "Mon Jun 1, 9:02 AM CT",
    commitments: [
      { id: "c-aisha-1", title: "Instrument adoption funnel telemetry",
        desc: "Add events across the activation funnel and surface them on the WAU board.",
        priority: "P1", workType: "STRATEGIC", confidence: "HIGH", alignment: "NEEDS_REVIEW",
        kind: "PLANNED", soId: "SO-1.1",
        dispute: {
          id: "d-aisha-1", status: "OPEN", flagType: "MISALIGNED",
          openedBy: "Dana Okafor",
          managerNote: "This maps to adoption, but the work reads like reliability — re-link it to a Platform-reliability outcome or explain the connection.",
          icResponse: null,
        } },
      { id: "c-aisha-2", title: "Cut onboarding time-to-first-locked-plan",
        desc: "Remove two steps from first-run so a new workspace locks faster.",
        priority: "P0", workType: "STRATEGIC", confidence: "MEDIUM", alignment: "ALIGNED",
        kind: "PLANNED", soId: "SO-1.2" },
      { id: "c-aisha-3", title: "Review Q2 support escalation themes",
        desc: "Cluster last quarter's escalations and brief the squad.",
        priority: "P2", workType: "MAINTENANCE", confidence: "MEDIUM", alignment: "ALIGNED",
        kind: "PLANNED", soId: "SO-2.2" },
      { id: "c-aisha-4", title: "Pair on platform latency profiling",
        desc: "Profile the p95 hot path with the platform squad.",
        priority: "P1", workType: "STRATEGIC", confidence: "HIGH", alignment: "NEEDS_REVIEW",
        kind: "PLANNED", soId: "SO-3.2" },
    ],
  },

  // R1 Priya — LOCKED, awaiting review (clean)
  priya: {
    owner: "priya", state: "LOCKED", planned: 3, unplanned: 0,
    managerReview: { status: "NOT_REVIEWED", reviewDue: "Jun 2, 5:00 PM", overdue: false },
    locked: "Mon Jun 1, 8:30 AM CT",
    commitments: [
      { id: "c-priya-1", title: "Lift activation WAU dashboard", desc: "Ship the weekly-active board GTM reviews.",
        priority: "P0", workType: "STRATEGIC", confidence: "HIGH", alignment: "ALIGNED", kind: "PLANNED", soId: "SO-1.1" },
      { id: "c-priya-2", title: "Reconciliation runbook v2", desc: "Document the new carry-forward flow.",
        priority: "P1", workType: "STRATEGIC", confidence: "MEDIUM", alignment: "NEEDS_REVIEW", kind: "PLANNED", soId: "SO-2.3" },
      { id: "c-priya-3", title: "Triage inbound onboarding tickets", desc: "Clear the activation support queue.",
        priority: "P2", workType: "MAINTENANCE", confidence: "MEDIUM", alignment: "ALIGNED", kind: "PLANNED", soId: "SO-2.2" },
    ],
  },

  // R2 Marco — LOCKED, review OVERDUE, failed Outlook sync
  marco: {
    owner: "marco", state: "LOCKED", planned: 3, unplanned: 1,
    managerReview: { status: "NOT_REVIEWED", reviewDue: "May 29, 5:00 PM", overdue: true },
    locked: "Thu May 28, 9:10 AM CT",
    commitments: [
      { id: "c-marco-1", title: "Harden the auth token refresh path", desc: "Close the silent-logout edge case.",
        priority: "P0", workType: "STRATEGIC", confidence: "HIGH", alignment: "ALIGNED", kind: "PLANNED", soId: "SO-3.2" },
      { id: "c-marco-2", title: "Cut new-workspace setup steps", desc: "Remove two first-run steps.",
        priority: "P1", workType: "STRATEGIC", confidence: "MEDIUM", alignment: "ALIGNED", kind: "PLANNED", soId: "SO-1.2" },
      { id: "c-marco-3", title: "Weekly platform health review", desc: "Run the uptime + latency review.",
        priority: "P2", workType: "MAINTENANCE", confidence: "HIGH", alignment: "ALIGNED", kind: "PLANNED", soId: "SO-3.1" },
      { id: "c-marco-4", title: "Patch CDN cache poisoning report", desc: "Mitigate the reported cache key issue.",
        priority: "P0", workType: "UNPLANNED", confidence: "HIGH", alignment: "ALIGNED", kind: "UNPLANNED", soId: "SO-3.3" },
    ],
  },

  // R4 Tomas — LOCKED, REVIEWED, with a fully-resolved dispute
  tomas: {
    owner: "tomas", state: "LOCKED", planned: 3, unplanned: 0,
    managerReview: { status: "REVIEWED", reviewedAt: "Jun 1, 4:40 PM", reviewer: "Dana Okafor", overdue: false },
    locked: "Mon Jun 1, 8:12 AM CT",
    commitments: [
      { id: "c-tomas-1", title: "Re-link the latency telemetry work", desc: "Profile and report p95 across the hot path.",
        priority: "P1", workType: "STRATEGIC", confidence: "HIGH", alignment: "ALIGNED", kind: "PLANNED", soId: "SO-3.2",
        dispute: { id: "d-tomas-1", status: "RESOLVED", flagType: "MISALIGNED", openedBy: "Dana Okafor",
          managerNote: "This looked like adoption work — can you tie it to reliability?",
          icResponse: "Re-linked to p95 latency; it's a reliability investment.", resolutionNote: "Agreed, resolved." } },
      { id: "c-tomas-2", title: "Ship the squad reconciliation dashboard", desc: "Surface weekly reconciliation rate per squad.",
        priority: "P1", workType: "STRATEGIC", confidence: "MEDIUM", alignment: "ALIGNED", kind: "PLANNED", soId: "SO-2.3" },
      { id: "c-tomas-3", title: "On-call runbook refresh", desc: "Update the Sev-1 escalation steps.",
        priority: "P2", workType: "MAINTENANCE", confidence: "MEDIUM", alignment: "ALIGNED", kind: "PLANNED", soId: "SO-3.1" },
    ],
  },

  // Dana — her own RECONCILED plan (viewed as a normal IC; managerReview === null)
  dana: {
    owner: "dana", state: "RECONCILED", planned: 3, unplanned: 0,
    managerReview: null, locked: "Mon Jun 1, 7:40 AM CT", reconciled: "Thu Jun 5",
    commitments: [
      { id: "c-dana-1", title: "Lift activation WAU dashboard to v2",
        desc: "Ship the weekly-active dashboard the GTM team reviews on Mondays.",
        priority: "P0", workType: "STRATEGIC", confidence: "HIGH", alignment: "ALIGNED",
        kind: "PLANNED", soId: "SO-1.1", outcome: "COMPLETED", outcomeNote: "Shipped Tue; 71% WAU." },
      { id: "c-dana-2", title: "Rewrite the reconciliation runbook (v2)",
        desc: "Update the close-the-week runbook for the new carry-forward flow.",
        priority: "P1", workType: "STRATEGIC", confidence: "MEDIUM", alignment: "NEEDS_REVIEW",
        kind: "PLANNED", soId: "SO-2.3", outcome: "CARRIED_FORWARD", outcomeNote: "", carriedTo: NEXT_WEEK },
      { id: "c-dana-3", title: "Coach squad leads on RCDO linking",
        desc: "Run a 30-min clinic on linking commitments to Supporting Outcomes.",
        priority: "P2", workType: "MAINTENANCE", confidence: "MEDIUM", alignment: "ALIGNED",
        kind: "PLANNED", soId: "SO-2.3", outcome: "COMPLETED", outcomeNote: "Clinic done; 6 leads attended." },
    ],
  },
};

/* ---- Outlook sync records keyed by person ---- */
const SYNC = {
  marco: { status: "FAILED", safeMessage: "Calendar sync failed; you can retry.", attempts: 1, ref: "0af7…319c", canRetry: true },
  grace: { status: "SYNCED" },
  aisha: { status: "SYNCED" },
  dana:  { status: "SYNCED" },
};

/* ---- Manager command-center rows (§6.3) ---- */
const COMMAND_ROWS = [
  { person: "priya", planState: "LOCKED", review: "NOT_REVIEWED", reviewMeta: "Due Jun 2, 5:00 PM",
    overdue: false, reconcile: null, planned: 3, unplanned: 0, risks: [], action: "Review", actionTone: "neutral", syncFailed: false },
  { person: "marco", planState: "LOCKED", review: "NOT_REVIEWED", reviewMeta: "Was due May 29, 5:00 PM",
    overdue: true, reconcile: null, planned: 3, unplanned: 1, risks: [], action: "Review", actionTone: "neutral", syncFailed: true },
  { person: "aisha", planState: "LOCKED", review: "REVIEWED_WITH_DISPUTES", reviewMeta: "SLA met · Jun 1, 3:12 PM",
    overdue: false, reconcile: null, planned: 4, unplanned: 0,
    risks: [{ kind: "misaligned", n: 1 }, { kind: "needsReview", n: 1 }, { kind: "dispute", n: 1 }],
    action: "Open", actionTone: "secondary", syncFailed: false },
  { person: "tomas", planState: "LOCKED", review: "REVIEWED", reviewMeta: "Jun 1, 4:40 PM",
    overdue: false, reconcile: null, planned: 3, unplanned: 0,
    risks: [{ kind: "resolved", n: 1 }], action: "Open", actionTone: "secondary", syncFailed: false },
  { person: "grace", planState: "RECONCILING", review: "REVIEWED", reviewMeta: "Jun 1",
    overdue: false, reconcile: "In progress · 1 carry-fwd", planned: 3, unplanned: 1,
    risks: [{ kind: "blocked", n: 1 }, { kind: "carry", n: 1 }], action: "Open", actionTone: "secondary", syncFailed: false },
  { person: "sam", planState: "DRAFT", review: "NONE", reviewMeta: "— not locked",
    overdue: false, reconcile: null, planned: 2, unplanned: 0,
    risks: [{ kind: "unlinked", n: 1 }], action: null, actionTone: "disabled", syncFailed: false },
];

/* ---- At-a-glance strip counts (computed in spec; pinned here) ---- */
const GLANCE = { reports: 6, overdue: 1, disputes: 2, reconciling: 1, draft: 1, reviewedClean: 1 };

/* ---- Heatmap cells: report -> { DO-1, DO-2, DO-3 } each {count, risks[], draft?} ---- */
const HEATMAP = {
  priya: { "DO-1": { count: 2, risks: [] }, "DO-2": { count: 1, risks: [] }, "DO-3": { count: 1, risks: [] } },
  marco: { "DO-1": { count: 3, risks: ["OVERDUE_REVIEW", "UNREVIEWED"] }, "DO-2": { count: 0, risks: [] }, "DO-3": { count: 1, risks: ["OVERDUE_REVIEW", "UNREVIEWED"] } },
  aisha: { "DO-1": { count: 1, risks: ["NEEDS_REVIEW"] }, "DO-2": { count: 2, risks: [] }, "DO-3": { count: 1, risks: ["MISALIGNED"] } },
  tomas: { "DO-1": { count: 1, risks: [] }, "DO-2": { count: 1, risks: [] }, "DO-3": { count: 2, risks: [] } },
  grace: { "DO-1": { count: 4, risks: ["CARRY_FORWARD"] }, "DO-2": { count: 3, risks: ["BLOCKED"] }, "DO-3": { count: 0, risks: [] } },
  sam:   { "DO-1": { count: 0, risks: [] }, "DO-2": { count: 1, risks: [], draft: true }, "DO-3": { count: 0, risks: [] } },
};

/* ---- Heatmap drill-down content (Grace × DO-2, the §6.4 example) ---- */
const DRILLDOWN = {
  "grace|DO-2": {
    person: "grace", doTitle: "Operational excellence in delivery", planState: "RECONCILING",
    cellRisk: "BLOCKED",
    groups: [
      { soId: "SO-2.2", soTitle: "Median support first-response under 2 business hours",
        commitments: [{ id: "c-grace-3", title: "Cut triage handoff latency", priority: "P0", workType: "STRATEGIC",
          confidence: "HIGH", outcome: "BLOCKED", soId: "SO-2.2", dispute: false, kind: "PLANNED" }] },
      { soId: "SO-2.3", soTitle: "Hold weekly-commit reconciliation rate above 90%",
        commitments: [{ id: "c-grace-4", title: "Backfill last week's runbook gaps", priority: "P1", workType: "MAINTENANCE",
          confidence: "MEDIUM", outcome: "CARRIED_FORWARD", soId: "SO-2.3", dispute: false, kind: "UNPLANNED" }] },
    ],
  },
};

Object.assign(window, {
  RALLY_CRY, RCDO_TREE, SO_INDEX, breadcrumb, WEEK_LABEL, NEXT_WEEK,
  PEOPLE, REPORT_ORDER, PLANS, SYNC, COMMAND_ROWS, GLANCE, HEATMAP, DRILLDOWN,
});
